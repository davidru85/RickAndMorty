package io.github.davidru85.multiverse.data.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * The observation probes (`TEST-CONTRACT-006`, `TASK-027`, `DEC-074`; hardened by `TASK-104`,
 * `B2-R07`).
 *
 * Each probe performs the **minimum** request that answers one question and records what came back
 * with its date. Nothing here asserts an API guarantee: the output is evidence for a human, and a
 * failure is triaged rather than gating (`TESTING.md` §11.3). The request budget is deliberately
 * small — the service publishes no rate-limit contract (`API_SPECS.md` §9), the cadence is weekly,
 * and each probe is one request.
 *
 * `GAP-021` recorded six defects in the first implementation, and this revision closes each:
 *
 * - the loop had **no timeout**, so a hung connection held the scheduled job for its whole budget:
 *   both a connect and a request timeout are set now;
 * - the record was written **only after the whole loop**, so a later crash lost every earlier
 *   observation: each record is persisted as it is produced, and the aggregate is rewritten from
 *   the records that exist;
 * - a transport failure was caught by a **broad** handler that could swallow a defect in the
 *   probe's own code: only `IOException` and `InterruptedException` are treated as transport
 *   failures, and the interrupt flag is restored;
 * - the capture was written **before** the response was validated, so a truncated or non-JSON body
 *   landed on disk as if it were evidence: the body is validated first, and only then captured;
 * - any non-string, non-null `info.next` was classified as a **number**, including objects and
 *   arrays: the classification reads the actual JSON element type;
 * - `TESTING.md` §11.1 requires a **validation-error** observation that did not exist: the probe
 *   set now includes one.
 */
public object ObservationProbes {
    /** The only host this source set may reach (`REQ-SEC-001`). */
    public const val HOST: String = "https://rickandmortyapi.com"

    /**
     * How long a probe may take before it is recorded as a transport failure.
     *
     * A scheduled job that hangs proves nothing and blocks the runner; a bounded failure is a fact
     * a human can triage (`TESTING.md` §11.3).
     */
    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)

    /** The budget for the response itself, separate from connecting. */
    private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

    /** One probe: a name, the path it requests and the question it answers. */
    public data class Probe(
        val name: String,
        val path: String,
        val question: String,
    )

    /**
     * The minimal request set. One request each; no pagination walk, no batch sweep.
     *
     * `validation-error` is the observation `TESTING.md` §11.1 requires: a request the service
     * rejects, recorded with its status and its error body, so the shape of a rejection is evidence
     * rather than an assumption.
     */
    public val probes: List<Probe> =
        listOf(
            Probe("first-page", "/api/character", "published totals, page size and the next-page type"),
            Probe("filtered-empty", "/api/character?name=zzzznotreal", "the status and cacheability of a filtered miss"),
            Probe("batch-mixed", "/api/character/1,99999", "batch behaviour with one invalid id"),
            Probe("validation-error", "/api/character/99999999", "the status and error body of an out-of-range id"),
        )

    /**
     * Runs every probe once and writes `observations.json` plus one capture per probe under
     * [outputDirectory]. Returns the records, so a caller can assert the record shape.
     *
     * The aggregate is written **before** the loop starts (as an empty list) and after every probe,
     * so a run that dies mid-way leaves the observations it did make rather than nothing.
     */
    public fun run(
        outputDirectory: File,
        now: Instant = Instant.now(),
        probeSet: List<Probe> = probes,
    ): List<ObservationRecord> {
        outputDirectory.mkdirs()
        val captures = File(outputDirectory, "captures").apply { mkdirs() }
        val records = mutableListOf<ObservationRecord>()
        val aggregate = File(outputDirectory, "observations.json")
        // Persist from the first moment: an interrupted run keeps what it observed.
        aggregate.writeText(observationJson.encodeToString(records))

        val client: HttpClient =
            HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .build()

        probeSet.forEach { probe ->
            val url = HOST + probe.path
            val request =
                HttpRequest
                    .newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build()

            val response =
                try {
                    client.send(request, HttpResponse.BodyHandlers.ofString())
                } catch (failure: IOException) {
                    // A transport failure is itself an observation: the run records it and a human
                    // triages it (TESTING.md §11.3). Only transport causes are caught here; a defect
                    // in the probe's own code propagates instead of being recorded as "the API".
                    records += transportFailure(probe, url, now, failure::class.simpleName ?: "IOException")
                    aggregate.writeText(observationJson.encodeToString(records))
                    return@forEach
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    records += transportFailure(probe, url, now, "InterruptedException")
                    aggregate.writeText(observationJson.encodeToString(records))
                    return@forEach
                }

            // Validate before capturing: a body that is not the JSON the probe asked for is not
            // evidence, and writing it would leave a file that looks like one (`GAP-021`).
            val body = response.body().orEmpty()
            val parsed = parseBody(body)
            if (parsed == null) {
                records +=
                    ObservationRecord(
                        probe = probe.name,
                        observedAt = now.toString(),
                        request = url,
                        status = response.statusCode(),
                        cacheControl = response.headers().firstValue("Cache-Control").orElse(null),
                        etag = response.headers().firstValue("ETag").orElse(null),
                        notes =
                            listOf(
                                probe.question,
                                "the body is not a JSON object; it was not captured as evidence",
                            ),
                    )
                aggregate.writeText(observationJson.encodeToString(records))
                return@forEach
            }

            File(captures, "${probe.name}.json").writeText(body)

            records +=
                ObservationRecord(
                    probe = probe.name,
                    observedAt = now.toString(),
                    request = url,
                    status = response.statusCode(),
                    cacheControl = response.headers().firstValue("Cache-Control").orElse(null),
                    etag = response.headers().firstValue("ETag").orElse(null),
                    totals = totalsOf(parsed),
                    notes = listOf(probe.question),
                )
            aggregate.writeText(observationJson.encodeToString(records))
        }

        return records
    }

    private fun transportFailure(
        probe: Probe,
        url: String,
        now: Instant,
        cause: String,
    ) = ObservationRecord(
        probe = probe.name,
        observedAt = now.toString(),
        request = url,
        status = 0,
        notes = listOf("transport failure: $cause", probe.question),
    )

    /** The parsed body, or `null` when it is not the JSON object a probe asked for. */
    private fun parseBody(body: String): JsonObject? =
        runCatching {
            Json.parseToJsonElement(body) as? JsonObject
        }.getOrNull()

    /**
     * The pagination metadata a body publishes, read defensively: the fields are recorded when they
     * are present and left null otherwise, so a shape change is visible instead of crashing.
     */
    private fun totalsOf(root: JsonObject): ObservationRecord.Totals? {
        val info = root["info"] as? JsonObject ?: return null
        return ObservationRecord.Totals(
            count = info["count"]?.jsonPrimitive?.content?.toIntOrNull(),
            pages = info["pages"]?.jsonPrimitive?.content?.toIntOrNull(),
            nextType = nextTypeOf(info["next"]),
        )
    }

    /**
     * The classifier's surface for the offline regression suite (`TASK-104`, `GAP-021`).
     *
     * The rules are pure functions of a JSON element, so the suite pins them directly rather than
     * through a socket. Production code reaches them through [run].
     */
    internal object ExposedForTest {
        internal fun nextTypeOf(next: kotlinx.serialization.json.JsonElement?): String? = ObservationProbes.nextTypeOf(next)
    }

    /**
     * The kind of the `next` field as the payload actually carries it.
     *
     * The service publishes `null` for the last page and a URL otherwise; the classifier exists to
     * catch a *change* to that shape. Classifying every non-string as "number" — the previous rule —
     * hid an object or an array behind a wrong label (`GAP-021`).
     */
    private fun nextTypeOf(next: kotlinx.serialization.json.JsonElement?): String? =
        when (next) {
            null -> null
            is JsonPrimitive ->
                when {
                    next.content == "null" && !next.isString -> "null"
                    next.isString -> "string"
                    next.content.toLongOrNull() != null -> "number"
                    next.content == "true" || next.content == "false" -> "boolean"
                    else -> "other"
                }
            is JsonObject -> "object"
            is kotlinx.serialization.json.JsonArray -> "array"
        }
}

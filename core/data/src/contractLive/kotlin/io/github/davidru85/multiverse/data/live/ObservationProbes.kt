package io.github.davidru85.multiverse.data.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/**
 * The observation probes (`TEST-CONTRACT-006`, `TASK-027`, `DEC-074`).
 *
 * Each probe performs the **minimum** request that answers one question and records what came back
 * with its date. Nothing here asserts an API guarantee: the output is evidence for a human, and a
 * failure is triaged rather than gating (`TESTING.md` §11.3). The request budget is deliberately
 * small — the service publishes no rate-limit contract (`API_SPECS.md` §9), the cadence is weekly,
 * and each probe is one request.
 */
public object ObservationProbes {
    /** The only host this source set may reach (`REQ-SEC-001`). */
    public const val HOST: String = "https://rickandmortyapi.com"

    /** One probe: a name, the path it requests and the question it answers. */
    public data class Probe(
        val name: String,
        val path: String,
        val question: String,
    )

    /** The minimal request set. One request each; no pagination walk, no batch sweep. */
    public val probes: List<Probe> =
        listOf(
            Probe("first-page", "/api/character", "published totals, page size and the next-page type"),
            Probe("filtered-empty", "/api/character?name=zzzznotreal", "the status and cacheability of a filtered miss"),
            Probe("batch-mixed", "/api/character/1,99999", "batch behaviour with one invalid id"),
        )

    /**
     * Runs every probe once and writes `observations.json` plus one capture per probe under
     * [outputDirectory]. Returns the records, so a caller can assert the record shape.
     */
    public fun run(
        outputDirectory: File,
        now: Instant = Instant.now(),
        probeSet: List<Probe> = probes,
    ): List<ObservationRecord> {
        outputDirectory.mkdirs()
        val captures = File(outputDirectory, "captures").apply { mkdirs() }
        val records = mutableListOf<ObservationRecord>()
        val client: HttpClient =
            HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()

        probeSet.forEach { probe ->
            val url = HOST + probe.path
            val request =
                HttpRequest
                    .newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build()
            val response =
                runCatching { client.send(request, HttpResponse.BodyHandlers.ofString()) }
                    .getOrElse { failure ->
                        // A transport failure is itself an observation: the run records it and a human
                        // triages it (TESTING.md §11.3). It is neither a crash nor a merge signal.
                        records +=
                            ObservationRecord(
                                probe = probe.name,
                                observedAt = now.toString(),
                                request = url,
                                status = 0,
                                notes = listOf("transport failure: ${failure::class.simpleName}", probe.question),
                            )
                        return@forEach
                    }

            File(captures, "${probe.name}.json").writeText(response.body())

            records +=
                ObservationRecord(
                    probe = probe.name,
                    observedAt = now.toString(),
                    request = url,
                    status = response.statusCode(),
                    cacheControl = response.headers().firstValue("Cache-Control").orElse(null),
                    etag = response.headers().firstValue("ETag").orElse(null),
                    totals = totalsOf(response.body()),
                    notes = listOf(probe.question),
                )
        }

        File(outputDirectory, "observations.json").writeText(observationJson.encodeToString(records))
        return records
    }

    /**
     * The pagination metadata a body publishes, read defensively: the fields are recorded when they
     * are present and left null otherwise, so a shape change is visible instead of crashing.
     */
    private fun totalsOf(body: String): ObservationRecord.Totals? =
        runCatching {
            val root = Json.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
            val info = root["info"] as? JsonObject ?: return@runCatching null
            ObservationRecord.Totals(
                count = info["count"]?.jsonPrimitive?.content?.toIntOrNull(),
                pages = info["pages"]?.jsonPrimitive?.content?.toIntOrNull(),
                nextType =
                    info["next"]?.let { next ->
                        when {
                            next.jsonPrimitive.isString -> "string"
                            next.jsonPrimitive.content == "null" -> "null"
                            else -> "number"
                        }
                    },
            )
        }.getOrNull()
}

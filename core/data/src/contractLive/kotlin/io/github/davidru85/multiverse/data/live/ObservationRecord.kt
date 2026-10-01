package io.github.davidru85.multiverse.data.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One dated observation of the live API (`TEST-CONTRACT-006`, `TASK-027`, `DEC-074`).
 *
 * An observation **records** what the service returned on a date. It asserts no guarantee the
 * official documentation does not publish (`TESTING.md` §11.1, `API_SPECS.md` §1.1); a change it
 * detects is a signal to refresh the fixtures and the owning document, never a merge blocker.
 */
@Serializable
public data class ObservationRecord(
    @SerialName("probe") val probe: String,
    @SerialName("observedAt") val observedAt: String,
    @SerialName("request") val request: String,
    @SerialName("status") val status: Int,
    @SerialName("cacheControl") val cacheControl: String? = null,
    @SerialName("etag") val etag: String? = null,
    @SerialName("totals") val totals: Totals? = null,
    @SerialName("notes") val notes: List<String> = emptyList(),
) {
    /** Pagination metadata, recorded only when the response publishes it. */
    @Serializable
    public data class Totals(
        @SerialName("count") val count: Int? = null,
        @SerialName("pages") val pages: Int? = null,
        @SerialName("nextType") val nextType: String? = null,
    )
}

/** Serialises the records the probes write and their tests read. */
internal val observationJson: Json =
    Json {
        prettyPrint = true
        encodeDefaults = true
    }

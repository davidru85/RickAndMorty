package io.github.davidru85.multiverse.core.data.remote.rest

import kotlin.time.Instant

/**
 * The seconds a `Retry-After` value advises waiting, measured from [now] (`API_SPECS.md` §6.3).
 *
 * Two forms are read: delta-seconds, and an HTTP-date in IMF-fixdate form (`Sun, 06 Nov 1994
 * 08:49:37 GMT`); a date in the past reads as zero. Anything else — prose, a negative number, an
 * impossible date, the obsolete RFC 850 or asctime forms — is `null`, which means no automatic retry.
 */
internal fun retryAfterSeconds(
    value: String?,
    now: Instant,
): Long? {
    val text = value?.trim()
    if (text.isNullOrEmpty()) return null
    if (text.all { it in '0'..'9' }) return text.toLongOrNull()
    val match = IMF_FIXDATE.matchEntire(text) ?: return null
    val (_, day, month, year, hour, minute, second) = match.destructured
    val monthNumber = MONTHS.indexOf(month) + 1
    val date =
        try {
            Instant.parse("$year-${monthNumber.toString().padStart(2, '0')}-${day}T$hour:$minute:${second}Z")
        } catch (_: IllegalArgumentException) {
            return null
        }
    val millis = (date - now).inWholeMilliseconds
    return if (millis <= 0) 0 else (millis + 999) / 1_000
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private val IMF_FIXDATE =
    Regex("""(Mon|Tue|Wed|Thu|Fri|Sat|Sun), (\d{2}) (${MONTHS.joinToString("|")}) (\d{4}) (\d{2}):(\d{2}):(\d{2}) GMT""")

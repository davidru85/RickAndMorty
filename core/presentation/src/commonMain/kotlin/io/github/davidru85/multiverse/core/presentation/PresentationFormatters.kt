package io.github.davidru85.multiverse.core.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.model.LocationSummary

/**
 * The shared presentation formatters (`IC-017`): pure functions over domain values, with no clock, no
 * locale and no platform type. Localisable prose is always a [CopyKey]; only data-derived text is a
 * `String`, and `null` means "hide this row or tile" — never an empty or placeholder string.
 */
public interface PresentationFormatters {
    /** The status label; an unknown or unrecognised status is [unknownKey]. */
    public fun statusKey(status: CharacterStatus): CopyKey

    /** The single source of the "Unknown" presentation. */
    public fun unknownKey(): CopyKey

    /** A raw API value for display: absent, blank or "unknown" is [unknownKey], anything else is data. */
    public fun valueText(raw: String?): DisplayText

    /** The origin's dimension, or its parenthesised designation, or `null` when it carries neither. */
    public fun dimensionText(
        origin: LocationSummary,
        enrichRequested: Boolean,
    ): String?

    /** The first episode as "name · code", or `null` when enrichment was not requested or found none. */
    public fun firstSeenText(summaries: List<EpisodeSummary>?): String?

    /** Stub: the countdown `error_message_rate_limited` substitutes (`GAP-027`). */
    public fun rateLimitCountdown(retryAfterSeconds: Long?): String?

    /** Stub: the message of a failure (`ERROR_FLOW.md` §4). */
    public fun failureMessage(failure: ApiFailure): FailureMessage

    /** Stub: the shared full-surface error title. */
    public fun failureTitle(): CopyKey

    /** Stub: the retry affordance's key. */
    public fun retryAction(): CopyKey

    /** Stub: whether the retry policy may retry this failure automatically. */
    public fun isAutomaticallyRetryable(failure: ApiFailure): Boolean
}

/** The formatters of `UI_SPEC.md` §6.2–§6.3, shared by both platforms. */
public object DefaultPresentationFormatters : PresentationFormatters {
    private val DESIGNATION = Regex("""\(([^()]*)\)\s*$""")
    private const val SEPARATOR = " · "

    override fun statusKey(status: CharacterStatus): CopyKey =
        when (status) {
            CharacterStatus.Alive -> CopyKeys.STATUS_ALIVE
            CharacterStatus.Dead -> CopyKeys.STATUS_DEAD
            CharacterStatus.Unknown, is CharacterStatus.Unsupported -> unknownKey()
        }

    override fun unknownKey(): CopyKey = CopyKeys.VALUE_UNKNOWN

    override fun valueText(raw: String?): DisplayText = if (raw.isKnown()) DisplayText.Data(raw) else DisplayText.Copy(unknownKey())

    override fun dimensionText(
        origin: LocationSummary,
        enrichRequested: Boolean,
    ): String? {
        if (enrichRequested && origin.dimension.isKnown()) return origin.dimension
        return DESIGNATION
            .find(origin.name)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isKnown() }
    }

    override fun firstSeenText(summaries: List<EpisodeSummary>?): String? {
        val first = summaries?.firstOrNull() ?: return null
        return listOf(first.name, first.code).filter { it.isNotBlank() }.joinToString(SEPARATOR).ifEmpty { null }
    }

    override fun rateLimitCountdown(retryAfterSeconds: Long?): String? = null

    override fun failureMessage(failure: ApiFailure): FailureMessage = FailureMessage(CopyKeys.ERROR_MESSAGE_UNKNOWN)

    override fun failureTitle(): CopyKey = CopyKeys.ERROR_TITLE

    override fun retryAction(): CopyKey = CopyKeys.ACTION_RETRY

    override fun isAutomaticallyRetryable(failure: ApiFailure): Boolean = false

    @OptIn(kotlin.contracts.ExperimentalContracts::class)
    private fun String?.isKnown(): Boolean {
        kotlin.contracts.contract { returns(true) implies (this@isKnown != null) }
        return this != null && isNotBlank() && !trim().equals(UNKNOWN, ignoreCase = true)
    }

    private const val UNKNOWN = "unknown"
}

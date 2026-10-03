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

    /**
     * The countdown `error_message_rate_limited` substitutes (`GAP-027`, `ERROR_FLOW.md` §4.1).
     *
     * `null` when the server advised nothing usable, so the message renders without an invented
     * number; otherwise the advised seconds, which both platforms substitute identically.
     */
    public fun rateLimitCountdown(retryAfterSeconds: Long?): String?

    /** The message of [failure] (`ERROR_FLOW.md` §4), with the values its wording substitutes. */
    public fun failureMessage(failure: ApiFailure): FailureMessage

    /** The shared full-surface error title (`ERROR_FLOW.md` §4.1). */
    public fun failureTitle(): CopyKey

    /** The retry affordance's key (`ERROR_FLOW.md` §10). */
    public fun retryAction(): CopyKey

    /**
     * Whether the retry policy may re-attempt [failure] automatically (`ERROR_FLOW.md` §10,
     * `API-ERR-003`/`006`/`008`/`009`: never for TLS, another `4xx`, an empty body or a decode failure).
     */
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

    override fun rateLimitCountdown(retryAfterSeconds: Long?): String? =
        retryAfterSeconds?.takeIf { it >= 0 }?.toString()

    override fun failureMessage(failure: ApiFailure): FailureMessage =
        when (failure) {
            ApiFailure.Offline -> FailureMessage(CopyKeys.ERROR_MESSAGE_OFFLINE)
            ApiFailure.Timeout -> FailureMessage(CopyKeys.ERROR_MESSAGE_TIMEOUT)
            is ApiFailure.NotFound -> FailureMessage(CopyKeys.ERROR_MESSAGE_NOT_FOUND)
            is ApiFailure.InvalidRequest -> FailureMessage(CopyKeys.ERROR_MESSAGE_INVALID_REQUEST)
            is ApiFailure.RateLimited ->
                FailureMessage(
                    CopyKeys.ERROR_MESSAGE_RATE_LIMITED,
                    listOfNotNull(rateLimitCountdown(failure.retryAfterSeconds)),
                )
            is ApiFailure.Server -> FailureMessage(CopyKeys.ERROR_MESSAGE_SERVER)
            is ApiFailure.GraphQl -> FailureMessage(CopyKeys.ERROR_MESSAGE_GRAPHQL)
            ApiFailure.MalformedResponse -> FailureMessage(CopyKeys.ERROR_MESSAGE_MALFORMED)
            ApiFailure.EmptyBody -> FailureMessage(CopyKeys.ERROR_MESSAGE_EMPTY_BODY)
            is ApiFailure.Unknown -> FailureMessage(CopyKeys.ERROR_MESSAGE_UNKNOWN)
        }

    override fun failureTitle(): CopyKey = CopyKeys.ERROR_TITLE

    override fun retryAction(): CopyKey = CopyKeys.ACTION_RETRY

    override fun isAutomaticallyRetryable(failure: ApiFailure): Boolean =
        when (failure) {
            // API-ERR-001/002/004/007: the transient classes the bounded budget covers (DEC-084).
            ApiFailure.Offline, ApiFailure.Timeout, is ApiFailure.Server -> true
            // API-ERR-003 (TLS), 006 (other 4xx), 008 (empty body), 009 (malformed) and the rest are
            // terminal for an automatic attempt; the user may still retry (ERROR_FLOW.md 10).
            else -> false
        }

    @OptIn(kotlin.contracts.ExperimentalContracts::class)
    private fun String?.isKnown(): Boolean {
        kotlin.contracts.contract { returns(true) implies (this@isKnown != null) }
        return this != null && isNotBlank() && !trim().equals(UNKNOWN, ignoreCase = true)
    }

    private const val UNKNOWN = "unknown"
}

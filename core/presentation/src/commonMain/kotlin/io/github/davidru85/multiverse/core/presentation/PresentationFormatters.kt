package io.github.davidru85.multiverse.core.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.model.LocationSummary
import io.github.davidru85.multiverse.core.domain.result.ApiFailure

/**
 * The shared presentation formatters (`IC-017`): pure functions over domain values, with no clock, no
 * locale and no platform type. Localisable prose is always a [CopyKey]; only data-derived text is a
 * `String`, and `null` means "hide this row or tile" — never an empty or placeholder string.
 */
public interface PresentationFormatters {
    /** The status label; an unknown or unrecognised status is [unknownKey]. */
    public fun statusKey(status: CharacterStatus): CopyKey

    /** The gender label (`DEC-131`); an unknown or unrecognised gender is [unknownKey]. */
    public fun genderKey(gender: CharacterGender): CopyKey

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
     * `null` when the server advised nothing usable, so the message without a countdown renders
     * instead of an invented number; otherwise the advised seconds, which both platforms substitute
     * identically as a number (`DEC-123`).
     */
    public fun rateLimitCountdown(retryAfterSeconds: Long?): Long?

    /**
     * The number `characters_count` substitutes (`UI_SPEC.md` §6.2, `AC-REQ-FUNC-001-3`).
     *
     * It is the one place the total becomes text, so both platforms render the same number in the
     * same template; the total comes from `info.count` and is never a hardcoded constant.
     */
    public fun charactersCount(count: Int): String

    /** The message of [failure] (`ERROR_FLOW.md` §4), with the values its wording substitutes. */
    public fun failureMessage(failure: ApiFailure): FailureMessage

    /** The shared full-surface error title (`ERROR_FLOW.md` §4.1). */
    public fun failureTitle(): CopyKey

    /** The retry affordance's key (`ERROR_FLOW.md` §10). */
    public fun retryAction(): CopyKey

    /**
     * How a failed Detail load is recovered (`ERROR_FLOW.md` §4, §10, `DEC-131`): [Recovery.Back] for
     * a `NotFound`, which is terminal for the identifier, and [Recovery.Retry] for every other failure.
     */
    public fun recovery(failure: ApiFailure): Recovery

    /**
     * The message of the inline error that replaces the Detail's info list while the header stays
     * (`AC-REQ-FUNC-002-3`, `DEC-131`): the failure's own message when it is recovered by Back, which
     * a "Retry" wording would contradict, and `detail_error_inline` otherwise.
     */
    public fun inlineFailureMessage(failure: ApiFailure): FailureMessage

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

    override fun genderKey(gender: CharacterGender): CopyKey =
        when (gender) {
            CharacterGender.Female -> CopyKeys.GENDER_FEMALE
            CharacterGender.Male -> CopyKeys.GENDER_MALE
            CharacterGender.Genderless -> CopyKeys.GENDER_GENDERLESS
            CharacterGender.Unknown, is CharacterGender.Unsupported -> unknownKey()
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

    override fun rateLimitCountdown(retryAfterSeconds: Long?): Long? = retryAfterSeconds?.takeIf { it >= 0 }

    override fun charactersCount(count: Int): String = count.toString()

    override fun failureMessage(failure: ApiFailure): FailureMessage =
        when (failure) {
            ApiFailure.Offline -> FailureMessage(CopyKeys.ERROR_MESSAGE_OFFLINE)
            ApiFailure.Timeout -> FailureMessage(CopyKeys.ERROR_MESSAGE_TIMEOUT)
            is ApiFailure.NotFound -> FailureMessage(CopyKeys.ERROR_MESSAGE_NOT_FOUND)
            is ApiFailure.InvalidRequest -> FailureMessage(CopyKeys.ERROR_MESSAGE_INVALID_REQUEST)
            // A countdown key is never paired with nothing to substitute: without usable advice the
            // message that carries no placeholder is chosen instead (`DEC-123`).
            is ApiFailure.RateLimited ->
                rateLimitCountdown(failure.retryAfterSeconds)
                    ?.let { seconds -> FailureMessage(CopyKeys.ERROR_MESSAGE_RATE_LIMITED, listOf(MessageArgument.Number(seconds))) }
                    ?: FailureMessage(CopyKeys.ERROR_MESSAGE_RATE_LIMITED_NO_COUNTDOWN)
            is ApiFailure.Server -> FailureMessage(CopyKeys.ERROR_MESSAGE_SERVER)
            is ApiFailure.GraphQl -> FailureMessage(CopyKeys.ERROR_MESSAGE_GRAPHQL)
            ApiFailure.MalformedResponse -> FailureMessage(CopyKeys.ERROR_MESSAGE_MALFORMED)
            ApiFailure.EmptyBody -> FailureMessage(CopyKeys.ERROR_MESSAGE_EMPTY_BODY)
            is ApiFailure.Unknown -> FailureMessage(CopyKeys.ERROR_MESSAGE_UNKNOWN)
        }

    override fun failureTitle(): CopyKey = CopyKeys.ERROR_TITLE

    override fun retryAction(): CopyKey = CopyKeys.ACTION_RETRY

    override fun recovery(failure: ApiFailure): Recovery =
        when (failure) {
            // API-ERR-016: a detail 404 cannot succeed on another attempt (ERROR_FLOW.md 10).
            is ApiFailure.NotFound -> Recovery.Back
            else -> Recovery.Retry
        }

    override fun inlineFailureMessage(failure: ApiFailure): FailureMessage =
        when (recovery(failure)) {
            Recovery.Back -> failureMessage(failure)
            Recovery.Retry -> FailureMessage(CopyKeys.DETAIL_ERROR_INLINE)
        }

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

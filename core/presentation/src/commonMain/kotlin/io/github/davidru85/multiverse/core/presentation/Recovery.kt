package io.github.davidru85.multiverse.core.presentation

/**
 * How a failed Detail load is recovered (`IC-017`, `DEC-131`, `ERROR_FLOW.md` §4, §10), with the key of
 * the one affordance that offers it.
 *
 * [Retry] re-attempts the load with a fresh budget; [Back] leaves the surface, for a failure that no
 * attempt can fix — a detail `404` is terminal for that identifier (`API-ERR-016`).
 */
public enum class Recovery(
    public val actionKey: CopyKey,
) {
    Retry(CopyKeys.ACTION_RETRY),
    Back(CopyKeys.ACTION_BACK),
}

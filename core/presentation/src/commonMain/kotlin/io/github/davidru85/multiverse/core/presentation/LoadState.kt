package io.github.davidru85.multiverse.core.presentation

import io.github.davidru85.multiverse.core.domain.result.ApiFailure

/**
 * What a screen renders for its data (`IC-015`). [Loading] only while nothing is displayable, never
 * replacing [Content] without an explicit reset; [Empty] only when the newest attempt completed and
 * matched nothing; [Error] always carries the one `ApiFailure` that caused it. The copy for each state
 * is bound by `ERROR_FLOW.md`, not derived from this type.
 */
public sealed interface LoadState {
    public data object Loading : LoadState

    public data object Content : LoadState

    public data object Empty : LoadState

    public data class Error(
        public val failure: ApiFailure,
    ) : LoadState
}

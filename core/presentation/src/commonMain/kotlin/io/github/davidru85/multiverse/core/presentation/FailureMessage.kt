package io.github.davidru85.multiverse.core.presentation

/**
 * The copy of one failure (`IC-017`): the key the platform resolves, and the values its wording
 * substitutes.
 *
 * The number of a rate-limit countdown travels here as an argument rather than interpolated into a
 * shared string, so both platforms render the same value in their own resource file and neither can
 * format it its own way (`ERROR_FLOW.md` §4.1, `GAP-027`). A message with no placeholder carries an
 * empty list.
 */
public data class FailureMessage(
    public val key: CopyKey,
    public val arguments: List<String> = emptyList(),
)

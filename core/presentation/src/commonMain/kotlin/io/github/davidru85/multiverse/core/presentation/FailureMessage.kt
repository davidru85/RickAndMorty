package io.github.davidru85.multiverse.core.presentation

/**
 * The copy of one failure (`IC-017`): the key the platform resolves, and the values its wording
 * substitutes.
 *
 * The number of a rate-limit countdown travels here as an argument rather than interpolated into a
 * shared string, so both platforms render the same value in their own resource file and neither can
 * format it its own way (`ERROR_FLOW.md` §4.1, `GAP-027`). Each argument is **typed** (`DEC-123`): a
 * platform substitutes it with the specifier its type needs (`%d`/`%1$ld` for a number, `%s`/`%1$@`
 * for text) and never parses a string to guess which one it is. A message with no placeholder
 * carries an empty list, and a key with a placeholder is never paired with an empty list.
 */
public data class FailureMessage(
    public val key: CopyKey,
    public val arguments: List<MessageArgument> = emptyList(),
)

/** One value a message's wording substitutes, in the order of its positional specifiers. */
public sealed interface MessageArgument {
    /** A number, such as the rate-limit countdown in seconds. */
    public data class Number(
        public val value: Long,
    ) : MessageArgument

    /** Data-derived text, substituted unchanged. */
    public data class Text(
        public val value: String,
    ) : MessageArgument
}

/**
 * The arguments as the values a platform format call takes, in order: a [MessageArgument.Number] as
 * a `Long`, a [MessageArgument.Text] as a `String`. Android passes them to `stringResource` unchanged.
 */
public fun FailureMessage.formatArguments(): Array<Any> =
    arguments
        .map { argument ->
            when (argument) {
                is MessageArgument.Number -> argument.value
                is MessageArgument.Text -> argument.value
            }
        }.toTypedArray()

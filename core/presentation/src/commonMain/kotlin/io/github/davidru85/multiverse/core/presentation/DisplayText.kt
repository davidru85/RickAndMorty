package io.github.davidru85.multiverse.core.presentation

/**
 * One displayed value whose source decides how a platform renders it (`IC-016`, `IC-017`): data shown
 * as it is — a proper noun, a code, a number — or a copy key the platform resolves from its resources.
 * It lets a state carry "Unknown" without a raw API value or an English literal in shared code.
 */
public sealed interface DisplayText {
    /** Data-derived text, shown unchanged. */
    public data class Data(
        public val value: String,
    ) : DisplayText

    /** Localisable copy, resolved by the platform. */
    public data class Copy(
        public val key: CopyKey,
    ) : DisplayText
}

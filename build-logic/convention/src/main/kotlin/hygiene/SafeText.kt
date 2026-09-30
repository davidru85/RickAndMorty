package io.github.davidru85.multiverse.buildlogic.hygiene

/**
 * Renders repository-controlled text safely for a finding or a diagnostic (`TEST-UNIT-026`;
 * DEC-062).
 *
 * A Git path or a `.gitignore` pattern may legally contain a tab, a newline, a carriage
 * return or another C0/DEL control character. Interpolating one raw would make a single
 * finding look like several log lines, and would let the value drive the terminal with an
 * ANSI escape sequence. Every location and every reason therefore crosses this boundary
 * before it is rendered: the escape only affects the report, never the path used for a
 * filesystem or Git operation.
 */
internal object SafeText {

    private val ESCAPES = mapOf(
        '\\' to "\\\\",
        '\n' to "\\n",
        '\r' to "\\r",
        '\t' to "\\t",
    )

    /** One line, with control characters replaced by visible notation. */
    fun forReport(text: String): String = buildString(text.length) {
        text.forEach { character ->
            when {
                character in ESCAPES -> append(ESCAPES.getValue(character))
                character.code < 0x20 || character.code == 0x7f -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
    }
}

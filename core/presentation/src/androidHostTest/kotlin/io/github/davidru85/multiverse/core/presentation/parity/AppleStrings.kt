package io.github.davidru85.multiverse.core.presentation.parity

import java.io.File

/**
 * Reads an Apple `Localizable.strings`: `"key" = "value";` entries separated by whitespace and by
 * `/* … */` or `// …` comments, with `\"`, `\\`, `\n`, `\t`, `\r` and `\Uxxxx` escapes in quoted
 * strings. A file that does not follow that grammar fails loudly with its position, rather than being
 * read as fewer keys.
 */
object AppleStrings {
    fun parse(file: File): ParsedStrings = ParsedStrings.of(Reader(file.readText()).entries())

    private class Reader(
        private val text: String,
    ) {
        private var index = 0

        fun entries(): List<Pair<String, String>> =
            buildList {
                while (skipBlank()) {
                    val key = quoted()
                    expect('=')
                    val value = quoted()
                    expect(';')
                    add(key to value)
                }
            }

        /** Skips whitespace and comments; `false` at the end of the text. */
        private fun skipBlank(): Boolean {
            while (index < text.length) {
                when {
                    text[index].isWhitespace() -> index++
                    text.startsWith("/*", index) ->
                        index =
                            text.indexOf("*/", index + 2).also { require(it >= 0) { failure("an unterminated comment") } } + 2
                    text.startsWith("//", index) -> index = text.indexOf('\n', index).let { if (it < 0) text.length else it + 1 }
                    else -> return true
                }
            }
            return false
        }

        private fun expect(c: Char) {
            skipBlank()
            require(index < text.length && text[index] == c) { failure("`$c`") }
            index++
        }

        private fun quoted(): String {
            skipBlank()
            require(index < text.length && text[index] == '"') { failure("a quoted string") }
            index++
            return buildString {
                while (true) {
                    require(index < text.length) { failure("the closing quote") }
                    val c = text[index++]
                    when (c) {
                        '"' -> return@buildString
                        '\\' -> {
                            require(index < text.length) { failure("an escape") }
                            when (val next = text[index++]) {
                                'n' -> append('\n')
                                't' -> append('\t')
                                'r' -> append('\r')
                                'U', 'u' -> {
                                    append(text.substring(index, index + 4).toInt(HEX).toChar())
                                    index += 4
                                }
                                else -> append(next)
                            }
                        }
                        else -> append(c)
                    }
                }
            }
        }

        private fun failure(expected: String) = "expected $expected at offset $index"
    }

    private const val HEX = 16
}

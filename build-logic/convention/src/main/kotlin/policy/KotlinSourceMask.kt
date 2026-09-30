package io.github.davidru85.multiverse.buildlogic.policy

/**
 * Masks the parts of a Kotlin source file that must not count as a declaration
 * (`TEST-UNIT-014` P4, `TEST-UNIT-051` I5; DEC-061).
 *
 * The result has **exactly the same length** as the input: every character inside a
 * masked region is replaced by a space, except `\n` and `\r`, which are kept so that
 * line numbers and columns are unchanged.
 *
 * Masked regions:
 *
 * - a line comment, from the double-slash sequence to the end of the line;
 * - a block comment, from the slash-star sequence to its matching star-slash sequence,
 *   counting nested pairs as Kotlin does;
 * - when [maskStrings] is `true`, the **contents** of string literals, the delimiters
 *   kept: regular strings honouring backslash escapes, raw strings with no escapes,
 *   and the template expressions inside them.
 *
 * **Documented limitation.** With [maskStrings] `true`, an accessor used inside a
 * string template is masked with the string and is therefore not detected; none should
 * be, because a template is not a declaration.
 *
 * A character literal (`'x'`, `'\''`, a plain quoted double quote, `'|'`) is recognised
 * and skipped, so its quote character never opens or closes a string; its content is not
 * masked.
 *
 * Lexing is a single left-to-right pass over the states *code*, *line comment*,
 * *block comment (depth n)*, *string*, *raw string* and *char literal*. A comment opener
 * inside a string does not start a comment, and a double quote inside a comment does not
 * start a string.
 */
internal object KotlinSourceMask {

    fun mask(text: String, maskStrings: Boolean): String {
        val out = text.toCharArray()
        var i = 0
        var blockDepth = 0
        var inLineComment = false
        var inString = false
        var inRawString = false

        fun blank(from: Int, to: Int) {
            for (index in from until to) {
                if (out[index] != '\n' && out[index] != '\r') out[index] = ' '
            }
        }

        while (i < text.length) {
            val c = text[i]

            when {
                inLineComment -> {
                    if (c == '\n') inLineComment = false else out[i] = ' '
                    i++
                }

                blockDepth > 0 -> {
                    when {
                        c == '/' && text.getOrNull(i + 1) == '*' -> {
                            blockDepth++
                            out[i] = ' '; out[i + 1] = ' '
                            i += 2
                        }
                        c == '*' && text.getOrNull(i + 1) == '/' -> {
                            blockDepth--
                            out[i] = ' '; out[i + 1] = ' '
                            i += 2
                        }
                        c == '\n' || c == '\r' -> i++
                        else -> { out[i] = ' '; i++ }
                    }
                }

                inRawString -> {
                    if (c == '"' && text.startsWith("\"\"\"", i)) {
                        inRawString = false
                        if (maskStrings) blank(i, i + 3)
                        i += 3
                    } else {
                        if (maskStrings && c != '\n' && c != '\r') out[i] = ' '
                        i++
                    }
                }

                inString -> {
                    when {
                        c == '\\' -> {
                            if (maskStrings) blank(i, minOf(i + 2, text.length))
                            i += 2
                        }
                        c == '"' -> { inString = false; i++ }
                        else -> {
                            if (maskStrings && c != '\n' && c != '\r') out[i] = ' '
                            i++
                        }
                    }
                }

                else -> {
                    when {
                        c == '/' && text.getOrNull(i + 1) == '/' -> { inLineComment = true; i++ }
                        c == '/' && text.getOrNull(i + 1) == '*' -> { blockDepth = 1; i++ }
                        c == '"' && text.startsWith("\"\"\"", i) -> { inRawString = true; i += 3 }
                        c == '"' -> { inString = true; i++ }
                        c == '\'' -> { i = skipCharLiteral(text, i) }
                        else -> i++
                    }
                }
            }
        }
        return String(out)
    }

    /**
     * The index just past the character literal starting at [start], or `start + 1` when
     * the literal is unterminated. Its content is left untouched.
     */
    private fun skipCharLiteral(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            when {
                text[i] == '\\' -> i += 2
                text[i] == '\'' -> return i + 1
                text[i] == '\n' -> return i
                else -> i++
            }
        }
        return text.length
    }
}

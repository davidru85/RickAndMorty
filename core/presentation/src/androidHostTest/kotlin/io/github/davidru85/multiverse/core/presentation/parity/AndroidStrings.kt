package io.github.davidru85.multiverse.core.presentation.parity

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads an Android `strings.xml` as Android resolves it: XML entities, then the resource rules — a
 * value wrapped in double quotes keeps its whitespace, otherwise runs of whitespace collapse; an
 * unescaped double quote is dropped; and `\n`, `\t`, `\'`, `\"`, `\\`, `\@`, `\?` and `\uXXXX` are
 * escapes. Document type declarations are refused, so the parser reads no external entity.
 */
object AndroidStrings {
    fun parse(file: File): ParsedStrings {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isExpandEntityReferences = false
            }
        val strings =
            factory
                .newDocumentBuilder()
                .parse(file)
                .documentElement
                .getElementsByTagName("string")
        return ParsedStrings.of(
            (0 until strings.length).map { index ->
                val element = strings.item(index) as Element
                element.getAttribute("name") to unescape(element.textContent)
            },
        )
    }

    internal fun unescape(raw: String): String {
        val trimmed = raw.trim()
        val quoted = trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"') && !trimmed.endsWith("\\\"")
        val text = if (quoted) trimmed.substring(1, trimmed.length - 1) else trimmed.replace(Regex("\\s+"), " ")
        return buildString {
            var index = 0
            while (index < text.length) {
                val c = text[index]
                when {
                    c == '\\' && index + 1 < text.length -> {
                        val next = text[index + 1]
                        when (next) {
                            'n' -> append('\n')
                            't' -> append('\t')
                            'u' -> {
                                append(text.substring(index + 2, index + 6).toInt(HEX).toChar())
                                index += 4
                            }
                            else -> append(next)
                        }
                        index += 2
                    }
                    c == '"' -> index++
                    else -> {
                        append(c)
                        index++
                    }
                }
            }
        }
    }

    private const val HEX = 16
}

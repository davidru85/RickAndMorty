package io.github.davidru85.multiverse.core.presentation.parity

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads an Android `strings.xml` as Android resolves it: XML entities, then the resource rules — a
 * value wrapped in double quotes keeps its whitespace, otherwise runs of whitespace collapse; an
 * unescaped double quote is dropped; and `\n`, `\t`, `\'`, `\"`, `\\`, `\@`, `\?` and `\uXXXX` are
 * escapes. Document type declarations are refused, so the parser reads no external entity.
 *
 * Each quantity form of a `<plurals>` resource is read as its own entry, `name#quantity`, so the
 * verifier compares it as it compares a plain string (`DEC-132`).
 */
object AndroidStrings {
    fun parse(file: File): ParsedStrings {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isExpandEntityReferences = false
            }
        val root =
            factory
                .newDocumentBuilder()
                .parse(file)
                .documentElement
        val strings = root.getElementsByTagName("string")
        val plurals = root.getElementsByTagName("plurals")
        return ParsedStrings.of(
            (0 until strings.length).map { index ->
                val element = strings.item(index) as Element
                element.getAttribute("name") to unescape(element.textContent)
            } +
                (0 until plurals.length).flatMap { index ->
                    val plural = plurals.item(index) as Element
                    val items = plural.getElementsByTagName("item")
                    (0 until items.length).map { item ->
                        val element = items.item(item) as Element
                        CopyParity.pluralForm(plural.getAttribute("name"), element.getAttribute("quantity")) to
                            unescape(element.textContent)
                    }
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

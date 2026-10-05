package io.github.davidru85.multiverse.core.presentation.parity

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads an Apple `Localizable.stringsdict` (`DEC-132`): each key's plural variable, read as one entry
 * per quantity form, `key#quantity`, so the verifier compares it with the Android `<plurals>` item of
 * the same quantity.
 *
 * Only the one shape both platforms can express identically is accepted: a format key that is exactly
 * one plural variable (`%#@name@`) of `NSStringPluralRuleType`. Any other shape — text around the
 * variable, two variables, another rule type — fails loudly instead of being compared half-read.
 * Document type declarations are refused, so the parser reads no external entity.
 */
object AppleStringsDict {
    private val SINGLE_VARIABLE = Regex("""^%#@([A-Za-z_][A-Za-z0-9_]*)@$""")
    private val QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")

    fun parse(file: File): ParsedStrings {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isExpandEntityReferences = false
            }
        val plist =
            factory
                .newDocumentBuilder()
                .parse(file)
                .documentElement
        val root = requireNotNull(plist.elements().singleOrNull { it.tagName == "dict" }) { "${file.name}: no top-level dict" }
        return ParsedStrings.of(
            root.entries().flatMap { (key, value) -> forms(file, key, value) },
        )
    }

    private fun forms(
        file: File,
        key: String,
        entry: Element,
    ): List<Pair<String, String>> {
        require(entry.tagName == "dict") { "${file.name}: `$key` is not a dict" }
        val fields = entry.entries().toMap()
        val format = fields["NSStringLocalizedFormatKey"]?.textContent
        val variable =
            requireNotNull(format?.let { SINGLE_VARIABLE.find(it)?.groupValues?.get(1) }) {
                "${file.name}: `$key` must format exactly one plural variable, got `$format`"
            }
        val rule = requireNotNull(fields[variable]) { "${file.name}: `$key` declares no `$variable`" }.entries().toMap()
        require(rule["NSStringFormatSpecTypeKey"]?.textContent == "NSStringPluralRuleType") {
            "${file.name}: `$key.$variable` is not a plural rule"
        }
        return rule
            .filterKeys { it in QUANTITIES }
            .map { (quantity, value) -> CopyParity.pluralForm(key, quantity) to value.textContent }
    }

    /** The element children of this node, skipping text and comments. */
    private fun Element.elements(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    /** A plist `dict`'s `key`/value pairs, in order. */
    private fun Element.entries(): List<Pair<String, Element>> =
        elements().chunked(2).map { (key, value) ->
            require(key.tagName == "key") { "a plist dict must alternate key and value, got `${key.tagName}`" }
            key.textContent to value
        }
}

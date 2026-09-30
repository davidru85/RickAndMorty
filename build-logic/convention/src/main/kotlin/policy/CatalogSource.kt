package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * Reads the **source shape** of `gradle/libs.versions.toml` (`TEST-UNIT-014` P1,
 * `TEST-UNIT-051` I2; DEC-061).
 *
 * Gradle remains the semantic parser: this helper never interprets a version, it only
 * records the form a value was written in and the declaration order of the keys. Two
 * facts need it and nothing else can supply them:
 *
 * - `{ require = "1.11.0" }` and `"1.11.0"` resolve to the same `requiredVersion`, so the
 *   Gradle model cannot tell the forbidden rich form from the permitted plain one;
 * - the catalog object exposes alias sets, not file order, and the README inventory is
 *   required to follow declaration order.
 *
 * The reader tracks the current section, strips a comment only when `#` sits outside a
 * quoted string, keeps line numbers, and recognises rich-version keys (`require`,
 * `strictly`, `prefer`, `reject`, `rejectAll`) **only as TOML keys** — never inside a
 * comment or a quoted value.
 */
internal object CatalogSource {

    /** A rich-version key found in the catalog source, with the alias it belongs to. */
    data class RichForm(val alias: String, val line: Int, val key: String)

    /** The source facts the policy needs. */
    data class Inspection(
        val richForms: List<RichForm>,
        val libraryOrder: List<String>,
        val pluginOrder: List<String>,
    )

    /** The accessor of a `[libraries]` key: `androidx-compose-ui` -> `libs.androidx.compose.ui`. */
    fun libraryAccessor(key: String): String = "libs.${key.replace('-', '.')}"

    /** The accessor of a `[plugins]` key: `kotlin-compose` -> `libs.plugins.kotlin.compose`. */
    fun pluginAccessor(key: String): String = "libs.plugins.${key.replace('-', '.')}"

    /** Inspects the catalog source at [file]. */
    fun inspect(file: File): Inspection {
        val richForms = mutableListOf<RichForm>()
        val libraryOrder = mutableListOf<String>()
        val pluginOrder = mutableListOf<String>()

        var section: String? = null
        var tableAlias: String? = null

        file.readLines().forEachIndexed { index, rawLine ->
            val line = index + 1
            val text = stripComment(rawLine).trim()
            if (text.isEmpty()) return@forEachIndexed

            val header = HEADER.matchEntire(text)
            if (header != null) {
                val name = header.groupValues[1]
                section = name.substringBefore('.')
                tableAlias = name.substringAfter('.', "").ifEmpty { null }
                if (section == "libraries" && tableAlias != null) libraryOrder += tableAlias
                if (section == "plugins" && tableAlias != null) pluginOrder += tableAlias
                return@forEachIndexed
            }

            val assignment = ASSIGNMENT.matchEntire(text) ?: return@forEachIndexed
            val key = assignment.groupValues[1]
            val value = assignment.groupValues[2].trim()
            val alias = tableAlias ?: key

            // The key of a rich form is a TOML key only when it is the assignment's key or
            // sits inside an inline table on the assignment's right-hand side.
            val richKey = richKeyOf(key, value)

            when (section) {
                "versions" -> {
                    if (richKey != null) {
                        richForms += RichForm(alias = key, line = line, key = richKey)
                    } else if (!isPlainQuoted(value)) {
                        richForms += RichForm(alias = key, line = line, key = "require")
                    }
                }

                "libraries", "plugins" -> {
                    if (tableAlias == null && value.isEmpty()) {
                        // An inline library entry always carries a value; `[libraries.x]`
                        // is handled by the header branch above.
                        return@forEachIndexed
                    }
                    if (richKey != null) {
                        richForms += RichForm(alias = alias, line = line, key = richKey)
                    }
                }
            }

            if (tableAlias == null && section == "libraries") libraryOrder += key
            if (tableAlias == null && section == "plugins") pluginOrder += key
        }

        return Inspection(
            richForms = richForms.distinct(),
            libraryOrder = libraryOrder.distinct(),
            pluginOrder = pluginOrder.distinct(),
        )
    }

    /**
     * The rich-version key of an assignment, or `null`. It is the assignment's own key, or
     * one of the keys inside an inline table on its right-hand side; a word inside a quoted
     * value is not a key.
     */
    private fun richKeyOf(key: String, value: String): String? {
        if (key in RICH_KEYS) return key
        if (!value.startsWith("{")) return null
        val inner = value.removePrefix("{").removeSuffix("}").trim()
        val innerKey = inner.substringBefore('=').trim()
        return innerKey.takeIf { it in RICH_KEYS }
    }

    /** True when the value is a single double-quoted scalar with nothing else on the line. */
    private fun isPlainQuoted(value: String): Boolean =
        value.length >= 2 && value.startsWith('"') && value.endsWith('"') && value.count { it == '"' } == 2

    /** The line with its comment removed; a `#` inside a quoted string is content, not a comment. */
    private fun stripComment(line: String): String {
        var inString = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' && inString -> i += 2
                c == '"' -> { inString = !inString; i++ }
                c == '#' && !inString -> return line.take(i)
                else -> i++
            }
        }
        return line
    }

    /** A version-catalog declaration found in a settings file, with its line. */
    data class CatalogDeclaration(val line: Int, val name: String, val source: String?)

    /**
     * The version catalogs a settings file declares. The only catalog this build permits is
     * `libs`, sourced from the root `gradle/libs.versions.toml` (DEC-060); the reader records
     * every `create("…")` call and the file it imports, so P7 can reject the rest.
     */
    fun catalogsIn(settings: File): List<CatalogDeclaration> {
        val declarations = mutableListOf<CatalogDeclaration>()
        val text = settings.readText()
        val code = KotlinSourceMask.mask(text, maskStrings = false)
        CREATE_CALL.findAll(code).forEach { match ->
            val line = BuildScripts.lineOf(code, match.range.first)
            val name = match.groupValues[1]
            // The `from(files("…"))` of the same statement, if any.
            val tail = code.substring(match.range.last + 1, minOf(code.length, match.range.last + 1 + 200))
            val source = SOURCE_CALL.find(tail)?.groupValues?.get(1)
            declarations += CatalogDeclaration(line = line, name = name, source = source)
        }
        return declarations
    }

    private val CREATE_CALL = Regex("\\bcreate\\s*\\(\\s*\"([^\"]+)\"")
    private val SOURCE_CALL = Regex("\\bfrom\\s*\\(\\s*files\\s*\\(\\s*\"([^\"]+)\"")
    private val RICH_KEYS = setOf("require", "strictly", "prefer", "reject", "rejectAll")
    private val HEADER = Regex("^\\[(versions|libraries|plugins)(?:\\.([A-Za-z0-9_.\\-]+))?]$")
    private val ASSIGNMENT = Regex("^([A-Za-z0-9_.\\-]+)\\s*=\\s*(.*)$")
}

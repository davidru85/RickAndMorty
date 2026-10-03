package io.github.davidru85.multiverse.core.designsystem.tokens

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TEST-UNIT-035` — the committed Figma export and the Kotlin token objects agree in both
 * directions (`REQ-UX-002`, `AC-REQ-UX-002-1`, `DEC-022`, `DEC-102`).
 *
 * The comparison runs against `docs/figma/tokens.json`, the read-only export of the three
 * collections. One direction proves no Kotlin token drifts from the export; the other proves every
 * exported variable is either mapped by a token object or listed in the reviewed exclusion below,
 * so a variable added in Figma cannot be silently ignored.
 */
class TokensExportParityTest {

    private val export: JsonObject = run {
        val file = File(repoRoot(), EXPORT_PATH)
        assertTrue("the committed export must exist at $EXPORT_PATH", file.isFile)
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    @Test
    fun `TEST-UNIT-035 given_the_committed_export_when_every_exported_variable_is_read_then_it_is_mapped_or_explicitly_excluded`() {
        val exported = exportedVariables()
        assertTrue("the export must not be empty", exported.isNotEmpty())

        val mapped = MultiverseTokens.mappedNames()
        val unmapped = exported.keys - mapped - REVIEWED_EXCLUSIONS
        assertEquals(
            "every exported variable is mapped by a token object or listed in the reviewed exclusion",
            emptySet<String>(),
            unmapped,
        )

        val stale = REVIEWED_EXCLUSIONS - exported.keys
        assertEquals("an exclusion must name a variable the export still carries", emptySet<String>(), stale)
    }

    @Test
    fun `TEST-UNIT-035 given_the_token_objects_when_each_value_is_compared_then_it_equals_the_export`() {
        val exported = exportedVariables()
        val mappedValues = MultiverseTokens.entries()
        assertTrue("the token objects must not be empty", mappedValues.isNotEmpty())

        val drift = mappedValues.filter { (name, value) ->
            val expected = exported[name]
            expected == null || expected != value
        }
        assertEquals(
            "a Kotlin token must equal its export entry (Figma wins for values)",
            emptyMap<String, String>(),
            drift,
        )
    }

    private fun exportedVariables(): Map<String, String> {
        val variables = mutableMapOf<String, String>()
        export.getValue("collections").jsonArray.forEach { collection ->
            collection.jsonObject.getValue("variables").jsonArray.forEach { variable ->
                val entry = variable.jsonObject
                val name = entry.getValue("name").jsonPrimitive.content
                val value = entry.getValue("value").jsonPrimitive.content
                val previous = variables.put(name, value)
                assertEquals("a variable name must be unique across collections: $name", null, previous)
            }
        }
        return variables
    }

    private fun repoRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").isFile) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "the repository root could not be located from ${File(".").absolutePath}" }
    }

    private companion object {
        const val EXPORT_PATH = "docs/figma/tokens.json"

        /**
         * Variables the design system does not consume, reviewed one by one (`DEC-102`). The
         * iOS-only glass and label families belong to the Liquid Glass surface (`TASK-052`) and
         * are not Android tokens; the rest are M3 roles and glass controls no Android surface
         * renders. A variable leaves this list only when a token maps it, and an entry that stops
         * existing in the export fails the test above.
         */
        val REVIEWED_EXCLUSIONS: Set<String> = setOf(
            // iOS-only glass and label tokens (UI_SPEC.md §3.2, TASK-052).
            "Glass/Fill",
            "Glass/Fill Strong",
            "Glass/Stroke Highlight",
            "Glass/Stroke Edge",
            "Glass/Tint Green",
            "Glass/Tint Violet",
            "Glass/Shadow",
            "Label/Primary",
            "Label/Secondary",
            "Label/Tertiary",
            "Radius/Glass Control",
            "Radius/Glass Card",
            "Radius/Glass Panel",
        )
    }
}

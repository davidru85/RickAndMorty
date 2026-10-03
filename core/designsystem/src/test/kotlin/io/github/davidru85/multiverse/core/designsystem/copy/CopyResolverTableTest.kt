package io.github.davidru85.multiverse.core.designsystem.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `TEST-UNIT-036`, the resolver-half (`DEC-100`, `REQ-FUNC-013`): the compile-checked name → resource
 * table and the shipped `res/values/strings.xml` carry exactly the same names, and the Spanish file
 * carries the same set, so a key cannot exist in one and not the other.
 *
 * The table itself is the compile-time guarantee; this case keeps it from drifting from the XML that
 * actually ships.
 */
class CopyResolverTableTest {
    @Test
    fun `TEST-UNIT-036 given_the_resolver_table_when_it_is_read_then_it_matches_the_shipped_resource_names`() {
        val english = resourceNames(File(resRoot(), "values/strings.xml"))
        val spanish = resourceNames(File(resRoot(), "values-es/strings.xml"))

        assertEquals("the resolver table must cover exactly the shipped English names", english, CopyResolver.names())
        assertEquals("both locales must carry the same names", english, spanish)
        assertTrue("the copy set must not be empty", english.isNotEmpty())
    }

    private fun resourceNames(file: File): Set<String> {
        assertTrue("the resource file must exist: ${file.path}", file.isFile)
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
        return (0 until strings.length)
            .map {
                strings
                    .item(it)
                    .attributes
                    .getNamedItem("name")
                    .nodeValue
            }.toSet()
    }

    private fun resRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").isFile) {
            directory = directory.parentFile
        }
        val root = requireNotNull(directory) { "the repository root could not be located" }
        return File(root, "core/designsystem/src/main/res")
    }
}

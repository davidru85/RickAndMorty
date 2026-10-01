package io.github.davidru85.multiverse.buildlogic.boundaries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-043` — the content-aware half of the structure rules (`GAP-012`, `TASK-088`).
 *
 * The rules must read declarations, not directory names, and must never accept a comment or a
 * string literal as proof of real code. These tests pin the mask, the destination form, the
 * `NavHost` detection and the file reader that feeds them.
 */
class FeatureSourcesTest {

    @Test
    fun `a comment and a string are masked while code is preserved`() {
        val source = """
            // package wrong.one
            /* package wrong.two */
            val note = "package wrong.three"
            package io.github.davidru85.multiverse.feature.discovery.navigation

            @Serializable
            data object CharacterList
        """.trimIndent()
        val masked = KotlinSources.mask(source)
        assertFalse(masked.contains("wrong.one"), "a line comment is masked")
        assertFalse(masked.contains("wrong.two"), "a block comment is masked")
        assertFalse(masked.contains("wrong.three"), "a string literal is masked")
        assertTrue(masked.contains("package io.github.davidru85.multiverse.feature.discovery.navigation"))
        assertTrue(masked.contains("data object CharacterList"), "executable code survives the mask")
    }

    @Test
    fun `a destination is a serializable declaration, in each accepted form`() {
        listOf(
            "@Serializable\ndata object Favorites",
            "@Serializable\ndata class CharacterDetail(val id: String)",
            "@Serializable\nenum class Screen { A }",
            "@Serializable\nsealed class Routes",
            "@Serializable @SomeAnnotation(value = 1)\npublic data object Settings",
        ).forEach { declaration ->
            assertTrue(
                destinationIn(declaration),
                "the accepted route form must be detected: ${declaration.replace("\n", " ")}",
            )
        }
    }

    @Test
    fun `a constant, an ordinary class, a comment or a string is not a destination`() {
        listOf(
            "const val NotADestination = 1",
            "class Helper",
            "// @Serializable data object CharacterList",
            "val note = \"@Serializable data object CharacterList\"",
            "fun screen() = Unit",
        ).forEach { declaration ->
            assertFalse(destinationIn(declaration), "this is not a destination: $declaration")
        }
    }

    @Test
    fun `the app-wide NavHost is detected in code but not in a comment or a string`() {
        assertTrue(navHostIn("fun screen() { NavHost(navController, startDestination = \"x\") }"))
        assertTrue(navHostIn("val graph: NavHost = something()"))
        assertFalse(navHostIn("// NavHost(navController, startDestination = \"x\")"))
        assertFalse(navHostIn("val note = \"the app shell owns the NavHost\""))
    }

    @Test
    fun `a source file reports its package, its destination and its NavHost use`() {
        val dir = kotlin.io.path.createTempDirectory("feature-sources").toFile()
        val file = dir.resolve("CharacterList.kt")
        file.writeText(
            """
            // Route declaration for the Discovery destination.
            package io.github.davidru85.multiverse.feature.discovery.navigation

            import kotlinx.serialization.Serializable

            @Serializable
            public data object CharacterList
            """.trimIndent() + "\n",
        )
        val source = FeatureSources.read(dir, file)
        assertEquals("io.github.davidru85.multiverse.feature.discovery.navigation", source.packageName)
        assertTrue(source.declaresDestination)
        assertFalse(source.namesAppWideNavHost)
        assertEquals("CharacterList.kt", source.path, "the reported path is relative to the given root")
        dir.deleteRecursively()
    }

    @Test
    fun `a file with no package directive reports none`() {
        val dir = kotlin.io.path.createTempDirectory("feature-sources").toFile()
        val file = dir.resolve("Bare.kt")
        file.writeText("val x = 1\n")
        assertEquals(null, FeatureSources.read(dir, file).packageName)
        dir.deleteRecursively()
    }

    @Test
    fun `the reader skips a missing source directory instead of failing`() {
        val dir = kotlin.io.path.createTempDirectory("feature-sources").toFile()
        assertEquals(emptyList(), FeatureSources.of(dir, dir.resolve("absent")))
        dir.deleteRecursively()
    }

    private fun destinationIn(declaration: String): Boolean =
        FeatureSources.analyseText("Probe.kt", declaration).declaresDestination

    private fun navHostIn(declaration: String): Boolean =
        FeatureSources.analyseText("Probe.kt", declaration).namesAppWideNavHost
}

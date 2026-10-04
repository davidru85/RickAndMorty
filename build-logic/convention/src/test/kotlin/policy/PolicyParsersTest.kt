package io.github.davidru85.multiverse.buildlogic.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The dependency-policy parsers, pinned directly (`TEST-UNIT-014`, `TEST-UNIT-051`).
 *
 * These cover the **pure** half of the policy: the shapes it reads from source text, the wrapper
 * URL form, the inventory cell grammar and the properties reader. They deliberately do not claim
 * that `GAP-011` is closed — several rules still infer declarations from source text, and this
 * suite pins only the shapes the policy documents as supported, plus the negative cases it rejects.
 *
 * The pipeline is exercised the way the task runs it: comment-masked source, then the pattern scan.
 */
class PolicyParsersTest {

    /** Exactly what `P4` does to a script before scanning it (`KotlinSourceMask`, strings kept). */
    private fun masked(text: String) = KotlinSourceMask.mask(text, maskStrings = false)

    // ---- P4: inline versions outside the catalog -------------------------------------------------

    @Test
    fun `an inline library coordinate and an inline plugin version are both rejected`() {
        val offenders = BuildScripts.inlineVersions(
            masked(
                """
                dependencies {
                    implementation("io.ktor:ktor-client-core:3.6.0")
                    testImplementation("junit:junit:4.13.2")
                }
                plugins {
                    id("com.example.plugin") version "1.2.3"
                }
                """.trimIndent(),
            ),
        ).map { it.value }
        assertEquals(3, offenders.size, "two inline coordinates and one inline plugin version: $offenders")
        assertTrue(offenders.any { it.contains("3.6.0") })
        assertTrue(offenders.any { it.contains("1.2.3") })
    }

    @Test
    fun `a catalog reference and a versionless coordinate are accepted`() {
        assertEquals(
            emptyList(),
            BuildScripts.inlineVersions(
                masked(
                    """
                    dependencies {
                        implementation(libs.ktor.client.core)
                        implementation("io.ktor:ktor-client-core")
                    }
                    """.trimIndent(),
                ),
            ).map { it.value },
        )
    }

    @Test
    fun `a commented-out inline version is not a violation`() {
        assertEquals(
            emptyList(),
            BuildScripts.inlineVersions(
                masked(
                    """
                    // implementation("io.ktor:ktor-client-core:3.6.0")
                    /* testImplementation("junit:junit:4.13.2") */
                    """.trimIndent(),
                ),
            ).map { it.value },
        )
    }

    @Test
    fun `an inline dependency constraint is one more rejected shape`() {
        val offenders = BuildScripts.inlineVersions(
            masked("""dependencies { implementation("g") { version { strictly("1.2.3") } } }"""),
        ).map { it.value }
        assertEquals(1, offenders.size, "the constraint DSL is an inline version: $offenders")
    }

    // ---- P5: the wrapper ------------------------------------------------------------------------

    @Test
    fun `the wrapper version is read from an exact release distribution`() {
        assertEquals(
            "9.7.0",
            GradleWrapper.version("https://services.gradle.org/distributions/gradle-9.7.0-bin.zip"),
        )
        assertEquals(
            "9.7",
            GradleWrapper.version("https://services.gradle.org/distributions/gradle-9.7-all.zip"),
            "the exact-release form admits MAJOR.MINOR",
        )
        assertNull(GradleWrapper.version("https://services.gradle.org/distributions/gradle-latest-bin.zip"))
        assertNull(GradleWrapper.version("https://services.gradle.org/distributions/gradle-9.7.0-rc-1-bin.zip"))
        assertNull(GradleWrapper.version(null))
    }

    /**
     * P5 constrains the **exact release form** and the SHA-256; it deliberately does not constrain
     * the host. Supply-chain questions about where the distribution comes from are `GAP-010`'s
     * (dependency-artifact verification), not this rule's, so this test pins the current behaviour
     * rather than asserting a host check the rule does not own.
     */
    @Test
    fun `the release form is matched independently of the host`() {
        assertEquals("9.7.0", GradleWrapper.version("https://mirror.example/gradle-9.7.0-bin.zip"))
    }

    // ---- Inventory (TEST-UNIT-051) --------------------------------------------------------------

    @Test
    fun `an inventory Declared-by cell is a list of code spans or a dash`() {
        assertEquals(listOf(":core:data"), MarkdownTable.codeSpanList("`:core:data`"))
        assertEquals(
            listOf(":androidApp", ":core:data"),
            MarkdownTable.codeSpanList("`:androidApp`, `:core:data`"),
        )
        assertEquals(emptyList(), MarkdownTable.codeSpanList("\u2014"))
        assertNull(MarkdownTable.codeSpanList("core:data"), "a bare path is not a code span")
    }

    // ---- Properties ------------------------------------------------------------------------------

    @Test
    fun `the properties reader unescapes values, ignores comments and follows Properties precedence`() {
        val file = kotlin.io.path.createTempFile("props", ".properties").toFile()
        file.writeText(
            """
            # a comment
            distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip

            distributionSha256Sum=abc123
            """.trimIndent() + "\n",
        )
        val properties = PropertiesFiles.read(file)
        assertEquals("https://services.gradle.org/distributions/gradle-9.7.0-bin.zip", properties["distributionUrl"])
        assertEquals("abc123", properties["distributionSha256Sum"])
        file.delete()
    }

    @Test
    fun `a missing properties file reads as empty rather than failing`() {
        val absent = kotlin.io.path.createTempDirectory("props").toFile().resolve("absent.properties")
        assertEquals(emptyMap(), PropertiesFiles.read(absent))
    }

    // ---- The table reader's fenced-block rule (TEST-UNIT-027, TEST-UNIT-030) ----------------------

    /**
     * A table's markers may be quoted inside a fenced example, as `SECURITY.md` documents its own
     * grammar. Only a marker outside every fence can delimit a parsed table, so the parser must skip
     * the fenced copy and find the real one.
     */
    @Test
    fun `a marker inside a fenced example is not the table's marker`() {
        val file = kotlin.io.path.createTempFile("fenced", ".md").toFile()
        file.writeText(
            """
            ````
            <!-- policy-table:begin -->
            | A | B |
            <!-- policy-table:end -->
            ````

            <!-- policy-table:begin -->
            | Name | Value |
            | --- | --- |
            | real | row |
            <!-- policy-table:end -->
            """.trimIndent() + "\n",
        )

        val table = MarkdownTable.parse(file, "<!-- policy-table:begin -->", "<!-- policy-table:end -->")

        assertEquals(emptyList(), table.problems.map { it.reason }, "the fenced marker must not be a problem")
        assertEquals(listOf("Name", "Value"), table.header)
        assertEquals(listOf(listOf("real", "row")), table.rows.map { it.cells })
        file.delete()
    }

    // ---- The mask the policy and the boundary check both rely on ---------------------------------

    @Test
    fun `the policy mask keeps strings and removes comments`() {
        val masked = KotlinSourceMask.mask(
            """
            // "io.ktor:ktor-client-core:1.0.0"
            val coordinate = "io.ktor:ktor-client-core:3.6.0"
            """.trimIndent(),
            maskStrings = false,
        )
        assertTrue(masked.contains("io.ktor:ktor-client-core:3.6.0"), "a coordinate is a string literal and is kept")
        assertTrue(!masked.contains("1.0.0"), "a commented coordinate is removed")
    }
}

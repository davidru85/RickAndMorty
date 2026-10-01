package io.github.davidru85.multiverse.buildlogic.hygiene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The deterministic half of `TEST-UNIT-026` (`HYG-03`, `SEC-026-*`).
 *
 * The Git-backed half of the hygiene task needs real object and history semantics and is exercised
 * by the task itself; these tests pin the pure rules: which paths are prohibited, which names are
 * carriers whatever their content, how a placeholder is distinguished from a value, and which
 * content pattern owns a finding. No test needs a repository, a network socket or the clock.
 */
class HygieneRulesTest {

    // ---- HYG-03: the tracked-path classes --------------------------------------------------------

    @Test
    fun `build output, IDE state and machine-local files are prohibited paths`() {
        assertTrue(HygieneRules.classifyTrackedProhibited("module/build/outputs/apk/app.apk")!!.contains("build output"))
        assertTrue(HygieneRules.classifyTrackedProhibited(".idea/workspace.xml") != null)
        assertTrue(HygieneRules.classifyTrackedProhibited("local.properties") != null)
        assertTrue(HygieneRules.classifyTrackedProhibited("App.xcuserstate") != null)
    }

    @Test
    fun `a credential carrier fails on its name even when the content looks harmless`() {
        listOf("keystore/release.jks", "signing/release.keystore", ".env", "google-services.json").forEach { path ->
            assertTrue(
                HygieneRules.classifyTrackedProhibited(path) != null,
                "$path is a carrier and must not be committed",
            )
            assertTrue(HygieneRules.pathRule(path) != null, "$path must name an owning rule")
        }
    }

    @Test
    fun `ordinary project files are not prohibited`() {
        listOf(
            "build-logic/convention/src/main/kotlin/hygiene/HygieneRules.kt",
            "docs/GUIDELINES.md",
            "gradle/libs.versions.toml",
            "build.gradle.kts",
        ).forEach { path ->
            assertNull(HygieneRules.classifyTrackedProhibited(path), "$path is an ordinary tracked file")
            assertNull(HygieneRules.pathRule(path), "$path names no credential rule")
        }
    }

    @Test
    fun `an environment file is a dotenv path and owns the dotenv rule`() {
        assertTrue(HygieneRules.isEnvironmentFile(".env.local"))
        assertTrue(HygieneRules.isEnvironmentFile(".env"))
        assertTrue(!HygieneRules.isEnvironmentFile("environment.md"))
        assertEquals(HygieneRules.RULE_DOTENV_ASSIGNMENT, HygieneRules.pathRule(".env"))
    }

    // ---- Placeholder versus value ----------------------------------------------------------------

    @Test
    fun `only unmistakable placeholders are non-values`() {
        assertTrue(HygieneRules.isNonValue(null))
        assertTrue(HygieneRules.isNonValue(""))
        assertTrue(HygieneRules.isNonValue("<your-key>"))
        assertTrue(HygieneRules.isNonValue("\${API_KEY}"))
        assertTrue(!HygieneRules.isNonValue("test-local-value-8f3a"), "a value is never suppressed for containing 'test'")
    }

    // ---- Content rules ---------------------------------------------------------------------------

    private fun findings(text: String): List<String> {
        val found = mutableListOf<String>()
        HygieneRules.scanContent(text) { ruleId, _ -> found += ruleId }
        return found
    }

    /**
     * Each sample is assembled from parts so the **scanned file** carries no credential-shaped
     * literal: `verifyRepositoryHygiene` scans tracked sources, and a test that hard-codes a real
     * pattern would make the repository it protects fail its own gate. The assembled string is
     * still exactly the shape the rule must match.
     */
    private fun sample(vararg parts: String) = parts.joinToString("")

    @Test
    fun `a PEM header owns its finding`() {
        val header = sample("-----BEGIN ", "RSA ", "PRIVATE KEY-----", "\n")
        assertEquals(listOf(HygieneRules.RULE_PEM_HEADER), findings(header))
    }

    @Test
    fun `a credential-shaped assignment owns its finding and a placeholder does not`() {
        val assignment = sample("api", "Key = \"", "AKIA", "IOSFODNN7", "EXAMPLE", "\"")
        assertTrue(
            findings(assignment).isNotEmpty(),
            "an assignment carrying a value-shaped secret is a finding",
        )
        assertEquals(emptyList(), findings("""apiKey = "<your-key>""""), "a placeholder is not a finding")
    }

    @Test
    fun `a basic-auth URL owns its finding`() {
        val url = sample("https://user:", "pass", "word@example.invalid/repo")
        assertTrue(findings(url).isNotEmpty())
    }

    @Test
    fun `a finding reports the line the offset falls on`() {
        val text = "line one\nline two\nmatch here"
        assertEquals(3, HygieneRules.lineAt(text, text.indexOf("match here")))
    }
}

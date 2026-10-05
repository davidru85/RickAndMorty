package io.github.davidru85.multiverse.core.presentation.parity

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `TEST-UNIT-082` — plural copy parity (`DEC-132`, `REQ-UX-008`, `AC-REQ-UX-008-1`, `TASK-112`).
 *
 * A plural key is carried as `<plurals>` on Android and in `Localizable.stringsdict` on Apple. Each
 * quantity form is compared as its own entry, `key#quantity`, through the same comparison the plain
 * keys use, so a form missing on one platform, a form only one platform has, and a form whose wording
 * diverges within a locale are each reported. These are controlled inputs under `copy-parity/plurals`;
 * the shipped copy is run through the same verifier by `CrossPlatformCopyParityTest`.
 */
class PluralCopyParityTest {
    private val canonical = CopyParity.canonical(plain = setOf("action_retry"), plurals = setOf("probe_items"))

    private val conforming = File(requireNotNull(javaClass.classLoader.getResource("copy-parity/plurals")).toURI())
    private val workspace: File = Files.createTempDirectory("plural-parity").toFile()

    @AfterTest
    fun release() {
        workspace.deleteRecursively()
    }

    /** A copy of the conforming control with [mutate] applied to one file, as a resource tree on disk. */
    private fun mutated(
        file: String,
        mutate: (String) -> String,
    ): File {
        conforming.copyRecursively(workspace, overwrite = true)
        val target = File(workspace, file)
        target.writeText(mutate(target.readText()))
        return workspace
    }

    private fun verify(root: File) =
        CopyParity.verify(canonical, androidResources = File(root, "android"), appleResources = File(root, "ios"))

    @Test
    fun `TEST-UNIT-082 given_a_plural_key_when_the_canonical_list_is_built_then_it_names_every_quantity_form`() {
        assertEquals(setOf("action_retry", "probe_items#one", "probe_items#other"), canonical)
    }

    @Test
    fun `TEST-UNIT-082 given_the_conforming_plural_control_when_verified_then_no_issue_is_reported`() {
        assertEquals(emptyList(), verify(conforming), "TEST-UNIT-082: the plurals and the stringsdict read alike per locale")
    }

    @Test
    fun `TEST-UNIT-082 given_a_quantity_form_missing_on_apple_when_verified_then_it_is_reported_there`() {
        val root =
            mutated("ios/en.lproj/Localizable.stringsdict") {
                it.replace("<key>one</key>\n            <string>Appears in %d item</string>\n", "")
            }

        assertEquals(listOf(ParityIssue.MissingKey(Platform.APPLE, "en", "probe_items#one")), verify(root))
    }

    @Test
    fun `TEST-UNIT-082 given_a_quantity_form_only_android_has_when_verified_then_it_is_an_extra_key`() {
        val root =
            mutated("android/values-es/strings.xml") {
                it.replace("    </plurals>", "        <item quantity=\"many\">Aparece en %d de elementos</item>\n    </plurals>")
            }

        assertEquals(listOf(ParityIssue.ExtraKey(Platform.ANDROID, "es", "probe_items#many")), verify(root))
    }

    @Test
    fun `TEST-UNIT-082 given_a_quantity_form_that_diverges_within_a_locale_when_verified_then_it_is_reported_with_both_values`() {
        val root = mutated("ios/es.lproj/Localizable.stringsdict") { it.replace("Aparece en %d elementos", "Sale en %d elementos") }

        assertEquals(
            listOf(
                ParityIssue.DivergentValue("es", "probe_items#other", android = "Aparece en %d elementos", apple = "Sale en %d elementos"),
            ),
            verify(root),
        )
    }

    @Test
    fun `TEST-UNIT-082 given_a_stringsdict_whose_format_wraps_the_plural_when_parsed_then_it_fails_loudly`() {
        // A format key with text around the variable renders differently from the Android form, so the
        // verifier refuses the shape rather than comparing half of the sentence.
        val root = mutated("ios/en.lproj/Localizable.stringsdict") { it.replace("<string>%#@count@</string>", "<string>Seen %#@count@</string>") }

        assertFailsWith<IllegalArgumentException> { verify(root) }
    }
}

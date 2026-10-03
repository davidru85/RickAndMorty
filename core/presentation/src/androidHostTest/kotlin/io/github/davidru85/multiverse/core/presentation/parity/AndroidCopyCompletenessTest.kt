package io.github.davidru85.multiverse.core.presentation.parity

import io.github.davidru85.multiverse.core.presentation.CopyKeys
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-036`, the real-resource case (`REQ-FUNC-013`, `AC-REQ-FUNC-013-1`, `DEC-100`): the
 * canonical key list is run against the **Android copy set that ships**, in both locales, through
 * the Android-completeness entry of the verifier — the same parser and comparison the
 * cross-platform [CopyParity.verify] uses.
 *
 * The cross-platform half stays `TASK-060`'s evidence while no Apple resource folder exists, so
 * `MissingResource(APPLE, …)` is expected here and is not reported by this entry. What it proves is
 * that a key added to `CopyKeys` without its English and Spanish strings fails, and that a key
 * shipped without being registered fails too.
 */
class AndroidCopyCompletenessTest {
    @Test
    fun `TEST-UNIT-036 given_the_shipped_android_copy_set_when_verified_then_every_canonical_key_resolves_in_both_locales`() {
        val issues =
            CopyParity.verifyAndroid(
                canonical = CopyKeys.all.map { it.value }.toSet(),
                androidResources = androidResourcesRoot(),
            )
        assertEquals(
            emptyList(),
            issues,
            "the shipped Android copy set carries exactly the canonical keys in en and es",
        )
    }

    @Test
    fun `TEST-UNIT-036 given_the_shipped_copy_set_when_the_locales_are_read_then_english_and_spanish_both_exist`() {
        val root = androidResourcesRoot()
        assertEquals(true, File(root, "values/strings.xml").isFile, "the English resource folder must exist")
        assertEquals(true, File(root, "values-es/strings.xml").isFile, "the Spanish resource folder must exist")
    }

    /**
     * The design system's resource root: the module that owns the one Android copy set
     * (`DEC-100`). The repository root is located from the working directory, so the test reads
     * the shipped file rather than a fixture.
     */
    private fun androidResourcesRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").isFile) {
            directory = directory.parentFile
        }
        val root = requireNotNull(directory) { "the repository root could not be located" }
        return File(root, "core/designsystem/src/main/res")
    }
}

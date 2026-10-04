package io.github.davidru85.multiverse.core.presentation.parity

import io.github.davidru85.multiverse.core.presentation.CopyKeys
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-036`, the cross-platform real-resource case (`REQ-FUNC-013`, `REQ-UX-008`,
 * `AC-REQ-FUNC-013-1`, `AC-REQ-UX-008-1`, `CONF-70`, `TASK-060`): the canonical key list is run
 * against **both apps' shipped copy** — the Android `strings.xml` pair and the Apple
 * `Localizable.strings` pair — through the same [CopyParity.verify] the fixture mutations prove.
 *
 * This is the entry `TASK-013`'s completeness test deliberately left open while no Apple resources
 * existed: its doc says the cross-platform half stays `TASK-060`'s evidence and that
 * `MissingResource(APPLE, …)` is expected there. With `iosApp/Resources/{en,es}.lproj` committed,
 * that expectation no longer holds, so the case asserts the full comparison and would fail on a key
 * missing from either platform, an extra key, a duplicate, or a value that diverges within a locale.
 *
 * English and Spanish are never compared with each other: each locale is checked for internal
 * consistency across the two platforms, which is what "identical copy" means in `REQ-UX-008`.
 */
class CrossPlatformCopyParityTest {
    @Test
    fun `TEST-UNIT-036 given_both_shipped_copy_sets_when_verified_then_the_platforms_agree_in_every_locale`() {
        val issues =
            CopyParity.verify(
                canonical = CopyKeys.all.map { it.value }.toSet(),
                androidResources = androidResourcesRoot(),
                appleResources = appleResourcesRoot(),
            )
        assertEquals(
            emptyList(),
            issues,
            "the Android and Apple copy sets must carry exactly the canonical keys, once each, " +
                "with identical values per locale",
        )
    }

    @Test
    fun `TEST-UNIT-036 given_both_shipped_copy_sets_when_the_locales_are_read_then_both_platforms_carry_each_locale`() {
        val android = androidResourcesRoot()
        val apple = appleResourcesRoot()
        assertEquals(true, File(android, "values/strings.xml").isFile, "the Android English set must exist")
        assertEquals(true, File(android, "values-es/strings.xml").isFile, "the Android Spanish set must exist")
        assertEquals(true, File(apple, "en.lproj/Localizable.strings").isFile, "the Apple English set must exist")
        assertEquals(true, File(apple, "es.lproj/Localizable.strings").isFile, "the Apple Spanish set must exist")
    }

    /**
     * The design system's Android resource root: the module that owns the one Android copy set
     * (`DEC-100`). The repository root is located from the working directory, so the case reads the
     * shipped files rather than a fixture.
     */
    private fun androidResourcesRoot(): File = File(repositoryRoot(), "core/designsystem/src/main/res")

    /** The iOS resource root: the folder holding `en.lproj` and `es.lproj` (`TASK-060`). */
    private fun appleResourcesRoot(): File = File(repositoryRoot(), "iosApp/Resources")

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").isFile) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "the repository root could not be located" }
    }
}

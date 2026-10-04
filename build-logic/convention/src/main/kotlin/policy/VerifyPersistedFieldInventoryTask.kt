package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * `TEST-UNIT-027` — the persisted-field inventory (`REQ-SEC-003`, `AC-REQ-SEC-003-1`).
 *
 * Every store source and both documents are declared inputs, so a key added in code or a field
 * removed from the classification re-runs the check instead of surviving a cached pass.
 */
@DisableCachingByDefault(because = "verification task with no outputs")
abstract class VerifyPersistedFieldInventoryTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val securityDocument: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contractsDocument: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val favoritesAndroid: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val favoritesApple: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val settingsAndroid: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val settingsApple: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val shell: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val imageLoader: RegularFileProperty

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = rootDirectory.get().asFile
        val sources =
            mapOf(
                PersistedFieldInventory.FAVORITES_ANDROID to favoritesAndroid.get().asFile,
                PersistedFieldInventory.FAVORITES_APPLE to favoritesApple.get().asFile,
                PersistedFieldInventory.SETTINGS_ANDROID to settingsAndroid.get().asFile,
                PersistedFieldInventory.SETTINGS_APPLE to settingsApple.get().asFile,
                PersistedFieldInventory.SHELL to shell.get().asFile,
                PersistedFieldInventory.IMAGE_LOADER to imageLoader.get().asFile,
            )
        val violations =
            PersistedFieldInventory
                .scan(securityDocument.get().asFile, contractsDocument.get().asFile, sources, root)
                .sortedBy { it.toString() }
        if (violations.isNotEmpty()) throw GradleException(render(violations))
        logger.lifecycle(
            "verifyPersistedFieldInventory passed: every store key is classified in SECURITY.md 3 and matches IC-021 " +
                "(${PersistedFieldInventory.TEST_ID}).",
        )
    }

    private fun render(violations: List<Violation>) = buildString {
        appendLine("The persisted-field inventory failed with ${violations.size} violation(s):")
        violations.forEach { appendLine(it) }
    }
}

package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * `multiverse.dependency.policy` — the repository's dependency policy as build checks
 * (DEC-061, TASK-015).
 *
 * Applied by the **root** build script only. It registers three verification tasks and
 * one aggregate:
 *
 * - `verifyDependencyPins` — `TEST-UNIT-014` (`AC-REQ-NFR-006-1`);
 * - `verifyDependencyRationale` — `TEST-UNIT-013` (`AC-REQ-NFR-002-2`);
 * - `verifyDependencyInventory` — `TEST-UNIT-051` (`AC-REQ-NFR-002-1`);
 * - `verifyDependencyPolicy` — the three above, wired into the root `check`.
 *
 * The scanned file set comes from the **build model**, not from a directory walk
 * (`D-01`): the main build's scripts are the existing settings files plus every
 * project's own build file, and the included build `build-logic/` contributes its
 * scripts and Kotlin sources under a fixed exclusion list. A nested checkout inside the
 * repository therefore cannot appear in the scan, and a project path comes from the
 * build rather than from a directory name.
 *
 * The catalog, the file sets and the project paths are all read at configuration time
 * and passed as `@Input`s, so every task is configuration-cache compatible and executes
 * with no `Project` access. The plugin adds no dependency of its own.
 */
class DependencyPolicyPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        check(target == target.rootProject) {
            "`multiverse.dependency.policy` is applied to the root project only; " +
                "found it on `${target.path}`."
        }

        target.pluginManager.apply("base")

        val catalog = CatalogCapture.capture(target.extensions.getByType<VersionCatalogsExtension>())

        val rootDir = target.rootDir
        val buildLogicDir = rootDir.resolve(BUILD_LOGIC)

        // The main build's scripts, taken from the build model: the settings files and
        // every declared project's own build file. A nested checkout is not a project.
        val mainSettingsFiles = SETTINGS_NAMES.map { rootDir.resolve(it) }.filter { it.isFile }
        val projectBuildFiles = target.allprojects.map { it.buildFile }.filter { it.isFile }
        val mainBuildScripts = target.files(mainSettingsFiles, projectBuildFiles)

        // Build state and IDE output are never sources.
        val buildLogicScripts = target.fileTree(buildLogicDir) {
            include("**/*.gradle.kts", "**/*.gradle")
            exclude(*BUILD_STATE_EXCLUDES)
        }
        val buildLogicSources = target.fileTree(buildLogicDir) {
            include("**/src/**/*.kt", "**/src/**/*.gradle.kts")
            exclude(*BUILD_STATE_EXCLUDES)
        }
        // The policy package is excluded from the name-lookup rule on purpose:
        // `CatalogCapture` must read the catalog this task verifies.
        val catalogLookupSources = target.fileTree(buildLogicDir) {
            include("**/src/**/*.kt", "**/src/**/*.gradle.kts")
            exclude(*BUILD_STATE_EXCLUDES, POLICY_PACKAGE_GLOB)
        }

        // Root-relative build-file path -> project path, taken from `Project.path` so a
        // `projectDir` remap cannot change a project's identity (F-05).
        val projectPaths = target.allprojects.associate { project ->
            project.buildFile.relativeTo(rootDir).invariantSeparatorsPath to project.path
        }

        // Paths that must not exist. They are declared even while absent, so creating one
        // invalidates a reusable configuration-cache entry (F-03).
        val forbiddenRoots = target.files(
            SETTINGS_NAMES.filter { it.endsWith(".gradle") }.map { rootDir.resolve(it) },
            rootDir.resolve("buildSrc"),
            buildLogicDir.resolve("buildSrc"),
        )

        // Both settings files, for the cross-build catalog check (F-02).
        val settingsFiles = target.files(
            SETTINGS_NAMES.map { rootDir.resolve(it) }.filter { it.isFile },
            buildLogicDir.resolve("settings.gradle.kts"),
        )

        val pins = target.tasks.register<DependencyPinsTask>("verifyDependencyPins") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-014: every external version is an exact pin, the build is Kotlin DSL without " +
                "`buildSrc`, and the Gradle wrapper is pinned by checksum (AC-REQ-NFR-006-1; DEC-061)."
            this.catalog.set(catalog)
            catalogFile.set(target.layout.projectDirectory.file("gradle/libs.versions.toml"))
            wrapperProperties.set(target.layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties"))
            rootDirectory.set(target.layout.projectDirectory)
            this.mainBuildScripts.from(mainBuildScripts)
            this.buildLogicScripts.from(buildLogicScripts)
            this.policySources.from(buildLogicSources)
            this.projectBuildFiles.from(projectBuildFiles)
            this.forbiddenRoots.from(forbiddenRoots)
            this.settingsFiles.from(settingsFiles)
        }

        val rationale = target.tasks.register<DependencyRationaleTask>("verifyDependencyRationale") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-013: every catalog entry has a named rationale, no concern has more than two " +
                "solutions, and the toolchain rows match their sources (AC-REQ-NFR-002-2; DEC-061)."
            this.catalog.set(catalog)
            designDocument.set(target.layout.projectDirectory.file("docs/DESIGN.md"))
            wrapperProperties.set(target.layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties"))
            daemonJvmProperties.set(target.layout.projectDirectory.file("gradle/gradle-daemon-jvm.properties"))
            rootDirectory.set(target.layout.projectDirectory)
        }

        val inventory = target.tasks.register<DependencyInventoryTask>("verifyDependencyInventory") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-051: the README dependency inventory lists exactly the catalog's libraries and " +
                "plugins with their version and declaration state (AC-REQ-NFR-002-1; DEC-061)."
            this.catalog.set(catalog)
            catalogFile.set(target.layout.projectDirectory.file("gradle/libs.versions.toml"))
            readme.set(target.layout.projectDirectory.file("README.md"))
            readmeEs.set(target.layout.projectDirectory.file("README.es.md"))
            this.buildScripts.from(mainBuildScripts, buildLogicScripts)
            this.catalogLookupSources.from(catalogLookupSources)
            this.projectPaths.set(projectPaths)
            rootDirectory.set(target.layout.projectDirectory)
        }

        val aggregate = target.tasks.register("verifyDependencyPolicy") {
            group = VERIFICATION_GROUP
            description = "Verifies the dependency policy: exact pins, per-entry rationale and README inventory " +
                "(TEST-UNIT-013, TEST-UNIT-014, TEST-UNIT-051; DEC-061)."
            dependsOn(pins, rationale, inventory)
        }

        target.tasks.named("check").configure { dependsOn(aggregate) }
    }

    private companion object {
        const val VERIFICATION_GROUP = "verification"
        const val BUILD_LOGIC = "build-logic"
        const val POLICY_PACKAGE_GLOB = "**/src/main/kotlin/policy/**"
        val SETTINGS_NAMES = listOf("settings.gradle.kts", "settings.gradle")
        val BUILD_STATE_EXCLUDES = arrayOf("**/build/**", "**/.gradle/**", "**/.kotlin/**")
    }
}

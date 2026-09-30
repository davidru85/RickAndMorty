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
 * The catalog, the file set and the project paths are all read at configuration time and
 * passed as `@Input`s, so every task is configuration-cache compatible and executes with
 * no `Project` access. The plugin adds no dependency of its own.
 */
class DependencyPolicyPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        check(target == target.rootProject) {
            "`multiverse.dependency.policy` is applied to the root project only; " +
                "found it on `${target.path}`."
        }

        target.pluginManager.apply("base")

        val catalog = CatalogCapture.capture(
            target.extensions.getByType<VersionCatalogsExtension>().named("libs"),
        )

        val buildScripts = target.fileTree(target.rootDir) {
            include("**/*.gradle.kts")
            exclude("**/build/**", ".gradle/**", ".kotlin/**", "prompts/**")
        }
        val policySources = target.fileTree(target.rootDir) {
            include("build-logic/**/src/**/*.kt")
            exclude("**/build/**", ".gradle/**", ".kotlin/**", "prompts/**")
        }

        val pins = target.tasks.register<DependencyPinsTask>("verifyDependencyPins") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-014: every external version is an exact pin, and the Gradle wrapper is " +
                "pinned by checksum (AC-REQ-NFR-006-1; DEC-061)."
            this.catalog.set(catalog)
            catalogFile.set(target.layout.projectDirectory.file("gradle/libs.versions.toml"))
            wrapperProperties.set(target.layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties"))
            this.buildScripts.from(buildScripts)
            this.policySources.from(policySources)
        }

        val rationale = target.tasks.register<DependencyRationaleTask>("verifyDependencyRationale") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-013: every catalog entry has a named rationale and no concern has more than " +
                "two solutions (AC-REQ-NFR-002-2; DEC-061)."
            this.catalog.set(catalog)
            designDocument.set(target.layout.projectDirectory.file("docs/DESIGN.md"))
        }

        val inventory = target.tasks.register<DependencyInventoryTask>("verifyDependencyInventory") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-051: the README dependency inventory lists exactly the catalog's libraries and " +
                "plugins with their version and declaration state (AC-REQ-NFR-002-1; DEC-061)."
            this.catalog.set(catalog)
            readme.set(target.layout.projectDirectory.file("README.md"))
            readmeEs.set(target.layout.projectDirectory.file("README.es.md"))
            this.buildScripts.from(buildScripts)
            projectDirectory.set(target.rootDir.absolutePath)
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
    }
}

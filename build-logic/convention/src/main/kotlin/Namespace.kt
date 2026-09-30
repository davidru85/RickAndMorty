package io.github.davidru85.multiverse.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * The package root, declared once as `multiverse.packageRoot` in the root
 * `gradle.properties` (OD-2). A missing property fails the build rather than
 * falling back to a literal, so the value has exactly one definition.
 */
internal val Project.packageRoot: Provider<String>
    get() = providers.gradleProperty("multiverse.packageRoot").orElse(
        providers.provider<String> {
            error(
                "The `multiverse.packageRoot` Gradle property is required " +
                    "(declared in the root gradle.properties).",
            )
        },
    )

/**
 * Namespace for a library module: the package root plus the project path with
 * its leading `:` removed, `:` replaced by `.` and `-` removed, so the result is
 * a lowercase package name with no underscores or hyphens (GUIDELINES.md §7.3).
 *
 * `:core:domain` -> `<root>.core.domain`
 * `:feature:character-detail` -> `<root>.feature.characterdetail`
 */
internal fun Project.libraryNamespace(): Provider<String> =
    packageRoot.map { root -> "$root.${path.trimStart(':').replace(':', '.').replace("-", "")}" }

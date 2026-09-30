package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.artifacts.VersionCatalog

/**
 * Reads the `libs` version catalog into a serializable snapshot at **configuration**
 * time, so the policy tasks need no `Project` at execution time and stay
 * configuration-cache compatible (DEC-061).
 */
internal object CatalogCapture {

    /** Captures every library, plugin and `[versions]` alias with its full version constraint. */
    fun capture(catalog: VersionCatalog): CatalogSnapshot {
        val libraries = catalog.libraryAliases.sorted().map { alias ->
            val library = catalog.findLibrary(alias).get().get()
            val constraint = library.versionConstraint
            val module = library.module
            CatalogLibrary(
                accessor = "libs.$alias",
                group = module.group,
                name = module.name,
                requiredVersion = constraint.requiredVersion,
                strictVersion = constraint.strictVersion,
                preferredVersion = constraint.preferredVersion,
                rejectedVersions = constraint.rejectedVersions,
            )
        }

        val plugins = catalog.pluginAliases.sorted().map { alias ->
            val plugin = catalog.findPlugin(alias).get().get()
            val constraint = plugin.version
            CatalogPlugin(
                accessor = "libs.plugins.$alias",
                pluginId = plugin.pluginId,
                requiredVersion = constraint.requiredVersion,
                strictVersion = constraint.strictVersion,
                preferredVersion = constraint.preferredVersion,
                rejectedVersions = constraint.rejectedVersions,
            )
        }

        val versions = catalog.versionAliases.sorted().map { alias ->
            val constraint = catalog.findVersion(alias).get()
            CatalogVersion(
                alias = alias,
                requiredVersion = constraint.requiredVersion,
                strictVersion = constraint.strictVersion,
                preferredVersion = constraint.preferredVersion,
                rejectedVersions = constraint.rejectedVersions,
            )
        }

        return CatalogSnapshot(
            libraries = libraries,
            plugins = plugins,
            versions = versions,
            bundles = catalog.bundleAliases.sorted().map { alias -> "libs.bundles.$alias" },
        )
    }
}

package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Reads the `libs` version catalog into a serializable snapshot at **configuration**
 * time, so the policy tasks need no `Project` at execution time and stay
 * configuration-cache compatible (DEC-061).
 */
internal object CatalogCapture {

    /**
     * Captures every library, plugin and `[versions]` alias of the `libs` catalog with its
     * full version constraint, and records the names of every declared catalog so P7 can
     * refuse a second one (DEC-060).
     */
    fun capture(catalogs: VersionCatalogsExtension): CatalogSnapshot {
        val catalog = catalogs.named(LIB_CATALOG)
        val libraries = catalog.libraryAliases.sorted().map { alias ->
            val library = catalog.findLibrary(alias).get().get()
            val constraint = library.versionConstraint
            val module = library.module
            CatalogLibrary(
                accessor = "libs.$alias",
                group = module.group,
                name = module.name,
                constraint = VersionConstraint(
                    required = constraint.requiredVersion,
                    strict = constraint.strictVersion,
                    preferred = constraint.preferredVersion,
                    rejected = constraint.rejectedVersions,
                ),
            )
        }

        val plugins = catalog.pluginAliases.sorted().map { alias ->
            val plugin = catalog.findPlugin(alias).get().get()
            val constraint = plugin.version
            CatalogPlugin(
                accessor = "libs.plugins.$alias",
                pluginId = plugin.pluginId,
                constraint = VersionConstraint(
                    required = constraint.requiredVersion,
                    strict = constraint.strictVersion,
                    preferred = constraint.preferredVersion,
                    rejected = constraint.rejectedVersions,
                ),
            )
        }

        val versions = catalog.versionAliases.sorted().map { alias ->
            val constraint = catalog.findVersion(alias).get()
            CatalogVersion(
                alias = alias,
                constraint = VersionConstraint(
                    required = constraint.requiredVersion,
                    strict = constraint.strictVersion,
                    preferred = constraint.preferredVersion,
                    rejected = constraint.rejectedVersions,
                ),
            )
        }

        return CatalogSnapshot(
            libraries = libraries,
            plugins = plugins,
            versions = versions,
            bundles = catalog.bundleAliases.sorted().map { alias -> "libs.bundles.$alias" },
            catalogNames = catalogs.catalogNames.sorted(),
        )
    }
}

/** The only version catalog this build declares (DEC-060). */
internal const val LIB_CATALOG = "libs"

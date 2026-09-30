package io.github.davidru85.multiverse.buildlogic.policy

import java.io.Serializable

/**
 * One library entry of the `libs` version catalog, captured at configuration time
 * and passed to the policy tasks as an `@Input` (DEC-061).
 *
 * The catalog is read once during configuration, so no task touches `Project` at
 * execution time and the tasks stay configuration-cache compatible.
 */
data class CatalogLibrary(
    val accessor: String,
    val group: String,
    val name: String,
    val requiredVersion: String,
    val strictVersion: String,
    val preferredVersion: String,
    val rejectedVersions: List<String>,
) : Serializable {

    /** `group:name`, the coordinate form `TEST-UNIT-051` compares the README against. */
    val coordinates: String get() = "$group:$name"

    /**
     * A platform BOM: its artifact name is `bom` or ends in `-bom`, and it is the only
     * versionless-entry governor (DEC-060).
     */
    val isBom: Boolean get() = name == "bom" || name.endsWith("-bom")

    /** True when the entry carries no constraint at all, so its effective version is a BOM's. */
    val versionless: Boolean
        get() = requiredVersion.isEmpty() && strictVersion.isEmpty() &&
            preferredVersion.isEmpty() && rejectedVersions.isEmpty()
}

/** One plugin entry of the `libs` version catalog, captured at configuration time (DEC-061). */
data class CatalogPlugin(
    val accessor: String,
    val pluginId: String,
    val requiredVersion: String,
    val strictVersion: String,
    val preferredVersion: String,
    val rejectedVersions: List<String>,
) : Serializable

/** One `[versions]` alias and its constraint, captured at configuration time (DEC-061). */
data class CatalogVersion(
    val alias: String,
    val requiredVersion: String,
    val strictVersion: String,
    val preferredVersion: String,
    val rejectedVersions: List<String>,
) : Serializable

/**
 * The configuration-time snapshot of the `libs` version catalog.
 *
 * It carries every library, every plugin and every `[versions]` alias with its full
 * version constraint, so `TEST-UNIT-014` can assert exact pins without a rich-version
 * form surviving unnoticed (`AC-REQ-NFR-006-1`).
 */
data class CatalogSnapshot(
    val libraries: List<CatalogLibrary>,
    val plugins: List<CatalogPlugin>,
    val versions: List<CatalogVersion>,
    val bundles: List<String>,
    val catalogNames: List<String>,
) : Serializable {

    /** Every library and plugin accessor, in catalog order: libraries first, then plugins. */
    val accessors: List<String> get() = libraries.map { it.accessor } + plugins.map { it.accessor }

    /**
     * The version an entry actually pins: its own, or — for a versionless entry — the
     * version of the BOM whose group equals or is a dot-prefix of the entry's group.
     */
    fun effectiveVersion(accessor: String): String? {
        val library = libraries.firstOrNull { it.accessor == accessor }
        if (library != null) {
            if (!library.versionless) return library.requiredVersion
            val bom = governingBom(library)
            return bom?.requiredVersion
        }
        return plugins.firstOrNull { it.accessor == accessor }?.requiredVersion
    }

    /** True when the entry has no version of its own and is governed by a BOM. */
    fun isVersionless(accessor: String): Boolean =
        libraries.firstOrNull { it.accessor == accessor }?.versionless == true

    /** The BOM governing a versionless library, or `null` when none does. */
    fun governingBom(library: CatalogLibrary): CatalogLibrary? = libraries.firstOrNull { candidate ->
        candidate.isBom && candidate.accessor != library.accessor && !candidate.versionless &&
            (candidate.group == library.group || library.group.startsWith("${candidate.group}."))
    }

    /** True when at least one BOM governs the entry's group. */
    fun isBomGoverned(library: CatalogLibrary): Boolean = governingBom(library) != null

    /** All plugin ids, in catalog order. */
    fun pluginIds(): Map<String, String> = plugins.associate { it.accessor to it.pluginId }
}

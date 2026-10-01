package io.github.davidru85.multiverse.buildlogic.boundaries

import java.io.Serializable

/**
 * The kind of a source set, as the boundary rules need to distinguish it.
 *
 * - [PRODUCTION] — a shared production source set (`commonMain`, `iosMain`, an Apple target's
 *   main source set, …).
 * - [ANDROID_UI] — an Android production source set (`androidMain` in a KMP module, or any
 *   production configuration of `:androidApp`).
 * - [TEST] — any test source set (`commonTest`, `androidUnitTest`, `androidHostTest`,
 *   `androidDeviceTest`, `iosTest`, the Android `test`/`androidTest` source sets).
 */
enum class SourceSetKind : Serializable { PRODUCTION, ANDROID_UI, TEST }

/**
 * One project dependency as a module **declares** it: the consumer, the configuration and source
 * set that declared it, and the producer.
 *
 * Edges are captured from `configuration.dependencies` — the declarations the consuming module's
 * own build script makes — never from `allDependencies`, which also reports what a configuration
 * inherits. That is what `DEC-057` makes reviewable: every project edge lives in the consuming
 * module's build script.
 */
data class DeclaredEdge(
    val consumer: String,
    val configuration: String,
    val sourceSet: String,
    val kind: SourceSetKind,
    val producer: String,
) : Serializable

/** One externally-declared module dependency, used for the `:core:domain` purity rule. */
data class DeclaredExternalDependency(
    val consumer: String,
    val configuration: String,
    val sourceSet: String,
    val group: String,
    val name: String,
) : Serializable {
    /** `group:name`, the coordinate form the allow-list is written in. */
    val coordinates: String get() = "$group:$name"
}

/** The module family a project belongs to, derived from its path. */
enum class ModuleKind : Serializable {
    CORE_DOMAIN,
    CORE_DATA,
    CORE_PRESENTATION,
    CORE_DESIGN_SYSTEM,
    CORE_TESTING,
    CORE_IOS,
    FEATURE,
    ANDROID_APP,

    /**
     * A Gradle container: the root project and the `:core`/`:feature` groupings. They carry no
     * code and own no module rule; the module set of ADR-0001 is the leaf projects they group.
     * They are *known*, so they are not reported by the fail-closed rule — the rule exists for a
     * module the check was never taught, not for the grouping the settings file declares.
     */
    CONTAINER,
    UNKNOWN,
}

/** One project: its identity, its module family and everything it declares. */
data class ProjectSnapshot(
    val path: String,
    val directory: String,
    val moduleKind: ModuleKind,
    val edges: List<DeclaredEdge>,
    val externalDependencies: List<DeclaredExternalDependency>,
    /**
     * Root-relative `/`-separated paths of the project's production source files under `src/`,
     * used only by the staged structure rules of `DEC-068`. A file name is enough to decide
     * package and destination presence; contents are read separately and only for the `S2` scan.
     */
    val sourceFiles: List<String> = emptyList(),
) : Serializable

/**
 * The configuration-time snapshot of the module graph. It is an `@Input` of
 * `verifyModuleBoundaries`, so the task never touches `Project` at execution time and stays
 * configuration-cache compatible.
 */
data class ModuleGraphSnapshot(val projects: List<ProjectSnapshot>) : Serializable {

    fun byKind(kind: ModuleKind): List<ProjectSnapshot> = projects.filter { it.moduleKind == kind }

    fun project(path: String): ProjectSnapshot? = projects.firstOrNull { it.path == path }

    /** Every project path in the snapshot, sorted. */
    val paths: List<String> get() = projects.map { it.path }.sorted()
}

/** Classifies a project by its path. Unknown paths are `UNKNOWN` so a rule can fail closed. */
internal fun moduleKindOf(path: String): ModuleKind = when {
    path == ":" || path == ":core" || path == ":feature" -> ModuleKind.CONTAINER
    path == ":core:domain" -> ModuleKind.CORE_DOMAIN
    path == ":core:data" -> ModuleKind.CORE_DATA
    path == ":core:presentation" -> ModuleKind.CORE_PRESENTATION
    path == ":core:designsystem" -> ModuleKind.CORE_DESIGN_SYSTEM
    path == ":core:testing" -> ModuleKind.CORE_TESTING
    path == ":core:ios" -> ModuleKind.CORE_IOS
    path == ":androidApp" -> ModuleKind.ANDROID_APP
    path.startsWith(":feature:") -> ModuleKind.FEATURE
    else -> ModuleKind.UNKNOWN
}

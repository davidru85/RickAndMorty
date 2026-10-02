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
    /** The architecture-relevant configuration whose effective classpath carries the edge. */
    val configuration: String,
    val sourceSet: String,
    val kind: SourceSetKind,
    val producer: String,
    /**
     * The configuration that actually **declares** the dependency; it differs from
     * [configuration] when the edge is inherited through `extendsFrom`, which is the case a
     * post-merge review reproduced: a rule must see the effective edge and still name the
     * declaration that introduced it (`GAP-012`).
     */
    val originConfiguration: String = configuration,
) : Serializable

/** One externally-declared module dependency, used for the `:core:domain` purity rule. */
data class DeclaredExternalDependency(
    val consumer: String,
    val configuration: String,
    val sourceSet: String,
    /**
     * The kind of the source set that carries the dependency, from the same classifier the edges
     * use. `R14` admits the approved test libraries only where this is [SourceSetKind.TEST]
     * (`DEC-089`).
     */
    val kind: SourceSetKind,
    val group: String,
    val name: String,
    /** The configuration that declares the module; see [DeclaredEdge.originConfiguration]. */
    val originConfiguration: String = configuration,
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
     * The Kotlin Multiplatform targets the module declares, by name.
     *
     * A target set is architecture: `DEC-080` permits a JVM target on a `:core:*` module only, and
     * `DEC-054` requires the Android and both Apple targets everywhere. The snapshot carries the
     * actual set so a rule can decide on it, instead of the check inferring it from the presence of
     * tasks (`TASK-101`, `GAP-018`).
     */
    val targets: List<String> = emptyList(),
    /**
     * The module-local opt-in properties this module declares, by name.
     *
     * `DEC-080` requires the opt-in to be **module-local**: a global, inherited or root-level
     * declaration is rejected, and the only way to tell them apart is to read the module's own
     * `gradle.properties`.
     */
    val moduleLocalProperties: List<String> = emptyList(),
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

/**
 * Classifies a project by its path. Unknown paths are `UNKNOWN` so a rule can fail closed.
 *
 * A `:feature:*` path is a feature **only when it is one of the five modules ADR-0001 accepts**
 * (`GAP-014`): classifying every `:feature:*` prefix as a valid feature made the rule set
 * open-ended, so an invented module inherited every feature rule and passed. A path outside the
 * accepted set is `UNKNOWN` and fails closed under `R13`.
 */
internal fun moduleKindOf(path: String): ModuleKind = when {
    path in ModuleSet.CONTAINERS -> ModuleKind.CONTAINER
    path == ":core:domain" -> ModuleKind.CORE_DOMAIN
    path == ":core:data" -> ModuleKind.CORE_DATA
    path == ":core:presentation" -> ModuleKind.CORE_PRESENTATION
    path == ":core:designsystem" -> ModuleKind.CORE_DESIGN_SYSTEM
    path == ":core:testing" -> ModuleKind.CORE_TESTING
    path == ":core:ios" -> ModuleKind.CORE_IOS
    path == ":androidApp" -> ModuleKind.ANDROID_APP
    path in ModuleSet.FEATURES -> ModuleKind.FEATURE
    else -> ModuleKind.UNKNOWN
}

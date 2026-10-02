package io.github.davidru85.multiverse.buildlogic.boundaries

/**
 * The **canonical module set** of ADR-0001 as amended by ADR-0010 (`DEC-055`), ADR-0012
 * (`DEC-058`) and ADR-0013 (`DEC-088`), written down once so the topology rule states what the build must contain rather
 * than accepting whatever `settings.gradle.kts` happens to include (`GAP-014`, `TASK-091`).
 *
 * Two facts live here and nowhere else:
 *
 * - [REQUIRED] — the leaf projects that must exist. Every one of them carries a module rule, so a
 *   build that has silently lost one would otherwise satisfy every remaining rule: the check would
 *   simply have less to evaluate. A missing leaf therefore fails closed.
 * - [PLANNED] — the leaf projects a later task introduces. `:core:ios` (ADR-0012) belongs to
 *   `TASK-078`; until that change promotes it, its presence is legal but its absence is not a
 *   violation. The distinction is deliberate: a project that exists is checked by `R12`, and a
 *   project that does not exist yet is not pretended to exist.
 *
 * The container projects (`:` root, `:core`, `:feature`) group the leaves, carry no code and own no
 * rule. They are listed here so the containers are not confused with leaves when the rule counts or
 * compares paths.
 */
internal object ModuleSet {

    /** Every leaf project ADR-0001 requires today, sorted. */
    val REQUIRED: Set<String> = setOf(
        ":androidApp",
        ":core:data",
        ":core:designsystem",
        // `DEC-088`, ADR-0013: the debug-only diagnostic API, created by `TASK-047`.
        ":core:diagnostics",
        ":core:domain",
        ":core:presentation",
        ":core:testing",
        ":feature:character-detail",
        ":feature:discovery",
        ":feature:episodes",
        ":feature:favorites",
        ":feature:settings",
    )

    /**
     * The five accepted feature modules (ADR-0001 as amended by ADR-0010). A `:feature:*` path
     * outside this set is not an accepted feature, so it fails closed instead of being classified
     * as one merely because of its prefix (`GAP-014`).
     */
    val FEATURES: Set<String> = setOf(
        ":feature:character-detail",
        ":feature:discovery",
        ":feature:episodes",
        ":feature:favorites",
        ":feature:settings",
    )

    /** A leaf project a later task introduces; legal when present, not required until then. */
    val PLANNED: Set<String> = setOf(":core:ios")

    /** Gradle grouping projects: they own no rule and are not leaves. */
    val CONTAINERS: Set<String> = setOf(":", ":core", ":feature")

    /** True when [path] is one of the leaf projects ADR-0001 requires today. */
    fun isRequired(path: String): Boolean = path in REQUIRED

    /** True when [path] is an accepted feature module. */
    fun isAcceptedFeature(path: String): Boolean = path in FEATURES
}

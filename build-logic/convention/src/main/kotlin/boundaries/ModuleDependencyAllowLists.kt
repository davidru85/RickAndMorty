package io.github.davidru85.multiverse.buildlogic.boundaries

/**
 * The external-coordinate allow-list for a module whose architecture fixes it, derived from
 * ADR-0001 / ADR-0008 / `DEC-010` / `DEC-066` and the version catalog (`GAP-012`, `TASK-088`).
 *
 * Two rules exist today:
 *
 * - `:core:domain` may declare the Kotlin standard library and `kotlinx-coroutines-core` only
 *   (`DEC-066`, rule `R14`).
 * - `:core:designsystem` may declare **Compose only** (ADR-0001, `DESIGN.md` §3.4 rule 4, rule
 *   `R15`): the Compose families are enumerated, so an unrelated library is rejected instead of
 *   being admitted by a group-name substring.
 */
internal object ModuleDependencyAllowLists {

    private const val KOTLIN = "org.jetbrains.kotlin"
    private const val KOTLINX = "org.jetbrains.kotlinx"

    /** The only external libraries `:core:domain` may declare (`DEC-066`). */
    val DOMAIN: Set<String> = setOf(
        "$KOTLINX:kotlinx-coroutines-core",
    )

    /**
     * The Compose families `:core:designsystem` may declare. `androidx.compose.*` is Compose by
     * definition; the Kotlin Compose compiler plugin is a Plugin Marker, not a library, so it never
     * reaches this list. `androidx.activity:activity-compose`, `androidx.lifecycle` and
     * `androidx.splashscreen` are deliberately absent: the design system is tokens and components,
     * and the shell owns the rest of the Compose surface.
     */
    val DESIGN_SYSTEM_PREFIXES: List<String> = listOf(
        "androidx.compose:",
        "androidx.compose.material3:",
        "androidx.compose.material:",
        "androidx.compose.ui:",
        "androidx.compose.foundation:",
        "androidx.compose.runtime:",
        "androidx.compose.animation:",
        "androidx.compose.animation.core:",
        "androidx.graphics:",
        "org.jetbrains.compose:",
    )

    /** True when [coordinates] (`group:name`) is Compose-only material for the design system. */
    fun isDesignSystemAllowed(coordinates: String): Boolean =
        DESIGN_SYSTEM_PREFIXES.any { prefix -> coordinates.startsWith(prefix) }

    /**
     * Artifacts the Kotlin/Android toolchain adds by itself to every module rather than a build
     * script declaring them. They are not an architecture choice and are therefore not evaluated
     * by the Compose-only rule (`GAP-012`).
     */
    fun isToolchainImplicit(coordinates: String): Boolean =
        coordinates == "org.jetbrains.kotlin:kotlin-stdlib" ||
            coordinates == "org.jetbrains.kotlin:kotlin-stdlib-common" ||
            coordinates.startsWith("org.jetbrains.kotlin:kotlin-android-extensions")
}

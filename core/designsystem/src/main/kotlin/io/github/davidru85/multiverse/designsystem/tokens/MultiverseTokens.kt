package io.github.davidru85.multiverse.designsystem.tokens

/**
 * The design system's token objects, and the one place the committed export is mapped to Kotlin
 * (`DEC-022`, `DEC-102`).
 *
 * Values are added per family by `TASK-042`; this declaration fixes the surface the parity test
 * `TEST-UNIT-035` reads. `entries()` returns every token as `"<collection group>/<name>"` paired
 * with its Kotlin value in export form (a hex string for colours, a number for dimensions), and
 * `mappedNames()` names the export variables those tokens cover.
 */
public object MultiverseTokens {
    /** Every mapped token, keyed by its export variable name, with its Kotlin value. */
    public fun entries(): Map<String, String> = emptyMap()

    /** The export variable names the token objects map. */
    public fun mappedNames(): Set<String> = emptySet()
}

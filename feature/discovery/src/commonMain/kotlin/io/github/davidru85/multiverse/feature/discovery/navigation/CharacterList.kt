// Route declaration for the Discovery destination — DESIGN.md §4.2.
// Each feature declares its own destination; the app shell owns the app-wide
// navigation graph (ADR-0001, rule 7 of DESIGN.md §3.4).

package io.github.davidru85.multiverse.feature.discovery.navigation

import kotlinx.serialization.Serializable

/** The character-list destination — `DESIGN.md` §4.2. */
@Serializable
public data object CharacterList

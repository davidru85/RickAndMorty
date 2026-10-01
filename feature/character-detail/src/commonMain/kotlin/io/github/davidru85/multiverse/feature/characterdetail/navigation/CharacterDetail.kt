// Route declaration for the Character detail destination — DESIGN.md §4.2.

package io.github.davidru85.multiverse.feature.characterdetail.navigation

import kotlinx.serialization.Serializable

/** The character-detail destination — `DESIGN.md` §4.2. */
@Serializable
public data class CharacterDetail(
    public val id: String,
)

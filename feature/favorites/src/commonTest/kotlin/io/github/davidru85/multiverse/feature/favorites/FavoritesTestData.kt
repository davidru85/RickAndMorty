package io.github.davidru85.multiverse.feature.favorites

import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.CharacterSummary
import io.github.davidru85.multiverse.core.domain.model.LocationSummary

/**
 * A summary whose every field is stated, so a case reads as data rather than as defaults.
 *
 * It is the same shape `FakeCatalogue` serves a page as, so a resolution case and a catalogue case
 * talk about the same value.
 */
internal fun summary(
    id: String,
    name: String = "Character $id",
    status: CharacterStatus = CharacterStatus.Alive,
    species: String = "Human",
): CharacterSummary =
    CharacterSummary(
        id = CharacterId(id),
        name = name,
        status = status,
        species = species,
        type = null,
        gender = CharacterGender.Unknown,
        lastKnownLocation = LocationSummary(id = null, name = "unknown"),
        imageUrl = "https://example.invalid/avatar/$id.jpeg",
    )

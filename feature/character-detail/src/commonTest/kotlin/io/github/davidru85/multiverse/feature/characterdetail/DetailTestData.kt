package io.github.davidru85.multiverse.feature.characterdetail

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.model.LocationSummary

/** A detail model whose every field is stated, so a case reads as data rather than as defaults. */
internal fun details(
    id: String = "1",
    name: String = "Rick Sanchez",
    species: String = "Human",
    status: CharacterStatus = CharacterStatus.Alive,
    gender: CharacterGender = CharacterGender.Male,
    origin: LocationSummary = LocationSummary(id = null, name = "Earth (C-137)"),
    lastKnownLocation: LocationSummary = LocationSummary(id = null, name = "Citadel of Ricks"),
    episodeIds: List<String> = listOf("1", "2", "3"),
    episodeSummaries: List<EpisodeSummary>? = null,
): CharacterDetails =
    CharacterDetails(
        id = CharacterId(id),
        name = name,
        status = status,
        species = species,
        type = null,
        gender = gender,
        origin = origin,
        lastKnownLocation = lastKnownLocation,
        imageUrl = "https://example.invalid/avatar/$id.jpeg",
        episodeIds = episodeIds.map(::EpisodeId),
        episodeSummaries = episodeSummaries,
        createdAt = null,
    )

/** The first episode summary, in the shape an enrichment of `character-detail.json`'s episodes returns. */
internal fun firstEpisode(
    name: String = "Pilot",
    code: String = "S01E01",
): EpisodeSummary = EpisodeSummary(EpisodeId("1"), name, code, "December 2, 2013")

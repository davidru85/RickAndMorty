package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterGender
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.model.CharacterStatus
import io.github.davidru85.multiverse.core.domain.model.CharacterSummary
import io.github.davidru85.multiverse.core.domain.model.EpisodeId
import io.github.davidru85.multiverse.core.domain.model.EpisodeSummary
import io.github.davidru85.multiverse.core.domain.model.LocationSummary
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.ApiWarning
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource

/**
 * The in-memory data the behavioural doubles serve (`TESTING.md` §6.1, `DEC-072`).
 *
 * It pages and filters the way the server does — a server-controlled page size, `info.next`
 * derived from the remaining items, a filtered first page with no match as an empty page with
 * unestablished totals, and a list `404` beyond the last page as `NotFound` (`CONTRACTS.md`
 * `IC-011`) — so a double built on it honours the seam rather than echoing what a test configured.
 * Responses are deterministic and derived from ids, never from positions.
 */
public class FakeCatalogue(
    characters: List<CharacterDetails>,
    episodes: List<EpisodeSummary> = emptyList(),
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private val characters: List<CharacterDetails> = characters.distinctBy { it.id }
    private val charactersById: Map<CharacterId, CharacterDetails> = this.characters.associateBy { it.id }
    private val episodesById: Map<EpisodeId, EpisodeSummary> =
        episodes.distinctBy { it.id }.associateBy { it.id }

    init {
        require(pageSize > 0) { "A page holds at least one character" }
    }

    /** One page of [filter], as the remote list endpoint would serve it. */
    internal fun page(
        filter: CharacterFilter,
        page: Int,
    ): DataResult<CharacterPage> {
        if (page < 1) return failure(ApiFailure.InvalidRequest("page"))
        val query = filter.query.trim()
        val matching =
            characters.filter { character ->
                (query.isEmpty() || character.name.contains(query, ignoreCase = true)) &&
                    filter.status.admits(character.status)
            }
        val filtered = query.isNotEmpty() || filter.status != StatusFilter.All
        if (matching.isEmpty() && filtered && page == 1) {
            val empty = CharacterPage(emptyList(), page, pageCount = null, totalCount = null, nextPage = null, previousPage = null)
            return DataResult.Success(empty, DataSource.NETWORK, isStale = false)
        }
        val pageCount = (matching.size + pageSize - 1) / pageSize
        if (page > pageCount) return failure(ApiFailure.NotFound(PAGE_RESOURCE, page.toString()))
        val items = matching.drop((page - 1) * pageSize).take(pageSize).map { it.toSummary() }
        val result =
            CharacterPage(
                characters = items,
                page = page,
                pageCount = pageCount,
                totalCount = matching.size,
                nextPage = (page + 1).takeIf { it <= pageCount },
                previousPage = (page - 1).takeIf { it >= 1 },
            )
        return DataResult.Success(result, DataSource.NETWORK, isStale = false)
    }

    /** The character with [id], without enrichment, or `null` when the catalogue has none. */
    internal fun details(id: CharacterId): CharacterDetails? = charactersById[id]?.copy(episodeSummaries = null)

    /**
     * The summaries of [ids] in the requested order, matched by id, plus one warning per id the
     * catalogue does not hold — the reconciliation rule of `API_SPECS.md` §6.1.
     */
    internal fun episodes(ids: List<EpisodeId>): Pair<List<EpisodeSummary>, List<ApiWarning>> {
        val requested = ids.distinct()
        val found = requested.mapNotNull { episodesById[it] }
        val warnings =
            requested
                .filterNot { it in episodesById }
                .map { ApiWarning(code = MISSING_RESOURCE, detail = it.value) }
        return found to warnings
    }

    private fun failure(failure: ApiFailure) = DataResult.Failure(failure, DataSource.NETWORK)

    private fun StatusFilter.admits(status: CharacterStatus): Boolean =
        when (this) {
            StatusFilter.All -> true
            StatusFilter.Alive -> status == CharacterStatus.Alive
            StatusFilter.Dead -> status == CharacterStatus.Dead
            StatusFilter.Unknown -> status == CharacterStatus.Unknown
        }

    private fun CharacterDetails.toSummary() =
        CharacterSummary(
            id = id,
            name = name,
            status = status,
            species = species,
            type = type,
            gender = gender,
            lastKnownLocation = lastKnownLocation,
            imageUrl = imageUrl,
        )

    public companion object {
        /** The server's observed page size (`API_SPECS.md` §4.3); a test may pass its own. */
        public const val DEFAULT_PAGE_SIZE: Int = 20

        /** The resource name a list `404` beyond the last page carries in `ApiFailure.NotFound`. */
        public const val PAGE_RESOURCE: String = "character-page"

        /** The warning code for a requested resource a response omitted. */
        public const val MISSING_RESOURCE: String = "missing-resource"

        /** A deterministic character whose every field derives from its [id]. */
        public fun character(
            id: String,
            name: String = "Character $id",
            status: CharacterStatus = CharacterStatus.Alive,
            species: String = "Human",
            episodeIds: List<String> = emptyList(),
        ): CharacterDetails =
            CharacterDetails(
                id = CharacterId(id),
                name = name,
                status = status,
                species = species,
                type = null,
                gender = CharacterGender.Unknown,
                origin = LocationSummary(id = null, name = "unknown"),
                lastKnownLocation = LocationSummary(id = null, name = "unknown"),
                imageUrl = "https://example.invalid/avatar/$id.jpeg",
                episodeIds = episodeIds.map(::EpisodeId),
                episodeSummaries = null,
                createdAt = null,
            )

        /** A deterministic episode whose every field derives from its [id]. */
        public fun episode(id: String): EpisodeSummary =
            EpisodeSummary(
                id = EpisodeId(id),
                name = "Episode $id",
                code = "S01E${id.padStart(2, '0')}",
                airDate = "December 2, 2013",
            )
    }
}

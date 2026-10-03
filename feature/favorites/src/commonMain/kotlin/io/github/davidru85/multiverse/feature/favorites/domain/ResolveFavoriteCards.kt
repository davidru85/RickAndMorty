package io.github.davidru85.multiverse.feature.favorites.domain

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterSummary
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** What one resolution of a favourite set produced (`IC-020`, `DESIGN.md` §4.5). */
public data class FavoriteCards(
    /** The summary of each id the read resolved, keyed by its canonical id. An id whose read failed is absent. */
    public val summaries: Map<CharacterId, CharacterSummary> = emptyMap(),
    /** The failure of the newest read, or `null` when every read succeeded. */
    public val failure: ApiFailure? = null,
)

/**
 * Resolves the favourite ids to the summaries their cards render from (`IC-020`, `CONF-79`,
 * `DESIGN.md` §4.5).
 *
 * `IC-020`'s sentence "this feature issues no remote request" cannot hold at the same time as its own
 * `items: List<CharacterCardUi>` and `IC-008`/`IC-013` storing ids only. `DESIGN.md` §4.5 — higher in the
 * precedence order, and the section `CONF-79` is recorded against — settles it: the UI shows the
 * favourite state instantly and re-fetches details through the **normal cached path**. So this use case
 * reads each id through `IC-007.details(id, enrich = false)`:
 *
 * - it is the seam's own single-id read, so it reuses the cache, the coalescing and the bounded retry
 *   the repository already owns (`ADR-0009` rule 7) — a second resolution of the same id inside the
 *   fresh window costs no network call, because the repository answers it from the cache;
 * - `enrich = false` asks for no episode batch: a card shows photo, name, status and species and nothing
 *   else (`UI_SPEC.md` §6.2), so the extra request would be paid for and thrown away.
 *
 * **The concurrency is bounded.** A stored set can grow to hundreds of ids, so one request per id
 * launched at once would be an unbounded fan-out. At most [maxConcurrent] reads are in flight at a
 * time; each id still costs exactly its own read, and the cache is what makes a repeat cheap. The bound
 * is a constructor parameter so a test asserts it, and its default is a small one, because the whole set
 * is read for one screen and the repository's coalescing already shares identical reads.
 *
 * It holds no state and never throws for an expected remote failure: a `DataResult.Failure` is returned
 * as a value ([FavoriteCards.failure]), and one failing id does not discard the ids that did resolve. A
 * `CancellationException` propagates unchanged (`IC-007`, `ERROR_FLOW.md` §6).
 */
public class ResolveFavoriteCards(
    private val repository: CharacterRepository,
    private val maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    init {
        require(maxConcurrent > 0) { "a bounded resolution needs a positive bound" }
    }

    /** Resolves every id in [ids], at most [maxConcurrent] at a time. */
    public suspend operator fun invoke(ids: Set<CharacterId>): FavoriteCards =
        withContext(dispatcher) {
            if (ids.isEmpty()) return@withContext FavoriteCards()
            val permits = Semaphore(maxConcurrent)
            val outcomes =
                coroutineScope {
                    ids
                        .sortedBy { it.value }
                        .map { id ->
                            async { permits.withPermit { id to repository.details(id, enrich = false) } }
                        }.awaitAll()
                }
            val summaries =
                outcomes
                    .mapNotNull { (id, outcome) ->
                        (outcome as? DataResult.Success<CharacterDetails>)?.let { success -> id to success.value.toSummary() }
                    }.toMap()
            FavoriteCards(
                summaries = summaries,
                failure = outcomes.firstNotNullOfOrNull { (_, outcome) -> (outcome as? DataResult.Failure)?.failure },
            )
        }

    /**
     * The one field set a card renders (`IC-016`), taken from the detail read so the section needs no
     * separate list request. It is `CharacterSummary`'s shape and not a second model: the reducer maps it
     * through `CharacterCardUi.from`, exactly as Discovery maps a page's own summaries.
     */
    private fun CharacterDetails.toSummary(): CharacterSummary =
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
        /**
         * How many reads may be in flight at once by default.
         *
         * Small on purpose: the whole stored set is read for one screen, the repository coalesces
         * identical requests (`REQ-REL-002`) and the cache answers a repeat inside the fresh window, so a
         * wider bound would only widen the burst without shortening the wait.
         */
        public const val DEFAULT_MAX_CONCURRENT: Int = 4
    }
}

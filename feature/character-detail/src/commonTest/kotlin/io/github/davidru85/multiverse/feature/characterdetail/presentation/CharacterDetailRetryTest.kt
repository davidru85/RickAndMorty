package io.github.davidru85.multiverse.feature.characterdetail.presentation

import io.github.davidru85.multiverse.core.data.favorites.LocalFavoritesRepository
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.feature.characterdetail.details
import io.github.davidru85.multiverse.feature.characterdetail.domain.GetCharacterDetails
import io.github.davidru85.multiverse.feature.characterdetail.domain.ToggleFavorite
import io.github.davidru85.multiverse.testing.FakeFavoritesStore
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-069` — a Detail retry supersedes the load in flight (`IC-019`, `REQ-FUNC-011`,
 * `TASK-111`).
 *
 * Every `Retry` starts a new load. Without cancelling the previous one, two loads race and the last
 * to *finish* wins, so a slow, older failure can overwrite a newer success. The repository here
 * answers each call only when the case releases it, which is what lets the case finish the loads in
 * the opposite order to the one they started in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterDetailRetryTest {
    @Test
    fun `TEST-UNIT-069 given_a_slow_load_when_a_retry_succeeds_first_then_the_superseded_load_cannot_overwrite_it`() =
        TestTime.run {
            val repository = GatedDetailRepository()
            val favorites =
                LocalFavoritesRepository(local = FakeFavoritesStore(), logger = ValidatingAppLogger.forRelease(RecordingLogSink()))
            val holder =
                CharacterDetailStateHolder(
                    id = CharacterId("1"),
                    header = null,
                    getDetails = GetCharacterDetails(repository),
                    toggleFavorite = ToggleFavorite(favorites),
                    observeFavoriteIds = ObserveFavoriteIds(favorites),
                    scope = backgroundScope,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    formatters = DefaultPresentationFormatters,
                )

            holder.start()
            runCurrent()
            holder.onIntent(CharacterDetailIntent.Retry)
            runCurrent()
            assertEquals(2, repository.pending.size, "TEST-UNIT-069: the first load and the retry both reached the repository")

            // The retry answers first, then the load it superseded answers late with a failure.
            repository.pending[1].complete(DataResult.Success(details(), DataSource.NETWORK, isStale = false))
            runCurrent()
            assertEquals(LoadState.Content, holder.state.value.loadState, "TEST-UNIT-069: the retry's success renders")
            repository.pending[0].complete(DataResult.Failure(ApiFailure.Offline, DataSource.NETWORK))
            runCurrent()

            assertEquals(
                LoadState.Content,
                holder.state.value.loadState,
                "TEST-UNIT-069: a superseded load's late failure must not overwrite the newer success",
            )
        }
}

/** An `IC-007` double whose every detail call waits until the case completes its own gate. */
private class GatedDetailRepository : CharacterRepository {
    val pending = mutableListOf<CompletableDeferred<DataResult<CharacterDetails>>>()

    override suspend fun page(
        filter: CharacterFilter,
        page: Int,
        policy: PageLoadPolicy,
    ): DataResult<CharacterPage> = error("the Detail holder never requests a page")

    override suspend fun details(
        id: CharacterId,
        enrich: Boolean,
    ): DataResult<CharacterDetails> {
        val gate = CompletableDeferred<DataResult<CharacterDetails>>()
        pending += gate
        return gate.await()
    }
}

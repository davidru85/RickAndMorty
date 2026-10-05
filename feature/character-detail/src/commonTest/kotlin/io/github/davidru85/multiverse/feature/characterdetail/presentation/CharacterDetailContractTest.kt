package io.github.davidru85.multiverse.feature.characterdetail.presentation

import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.rickAndMortyDefaults
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DefaultPresentationFormatters
import io.github.davidru85.multiverse.core.presentation.DisplayText
import io.github.davidru85.multiverse.core.presentation.LoadState
import io.github.davidru85.multiverse.testing.FixtureCatalog
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.RecordingLogSink
import io.github.davidru85.multiverse.testing.TestTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `TEST-CONTRACT-002` — the Detail journey over the **committed** fixture, driven through the real
 * REST adapter (`TESTING.md` §11, `DEC-030`; `TASK-002`, `TASK-023`).
 *
 * `:core:data`'s suite owns the decoding edge cases of `TEST-CONTRACT-002` (the empty-`type`
 * character, the resilience bodies). This is the feature half the backlog row names: the fixture's
 * bytes reach `RestCharacterRemoteDataSource`, are mapped to `CharacterDetails`, and then reduced to
 * the `IC-019` state — so the screen's content, its dimension tile and its info rows are shown to be
 * derived from the recorded API shape rather than from a hand-written object. The case reads the
 * sidecar for the request path, so a fixture replaced by a different observation fails here.
 */
class CharacterDetailContractTest {
    private val formatters = DefaultPresentationFormatters

    @Test
    fun `TEST-CONTRACT-002 given_the_committed_detail_fixture_when_the_adapter_decodes_it_then_the_state_renders_from_that_shape`() =
        TestTime.run { dispatcher ->
            val meta = FixtureCatalog.meta("character-detail.json")
            val (client, served) =
                MockHttp.client(
                    MockHttp.route("character-detail.json", urlContains = meta.path),
                    MockHttp.route("episode-batch.json", urlContains = "/episode/"),
                )
            val adapter =
                RestCharacterRemoteDataSource(
                    client.config { rickAndMortyDefaults() },
                    dispatcher,
                    MutableFakeClock(),
                    ValidatingAppLogger.forDebug(RecordingLogSink()),
                )

            val result = adapter.characterDetails(CharacterId("1"))
            val state =
                CharacterDetailReducer.render(
                    header = null,
                    result = result,
                    isFavorite = false,
                    enrichRequested = false,
                    formatters = formatters,
                )

            assertEquals(
                meta.path,
                io.ktor.http
                    .Url(served.single().url)
                    .encodedPath,
                "TEST-CONTRACT-002: the single-resource route the sidecar records",
            )
            val details = assertIs<DataResult.Success<*>>(result).value as io.github.davidru85.multiverse.core.domain.model.CharacterDetails
            assertNull(
                details.episodeSummaries,
                "TEST-CONTRACT-002: the adapter does not enrich; that is the repository's and the use case's job",
            )
            assertEquals(details.episodeIds.size, state.episodeCount, "TEST-CONTRACT-002: the count is the fixture's own episode list")
            assertEquals(LoadState.Content, state.loadState, "TEST-CONTRACT-002: a decoded detail is content")
            assertEquals("Rick Sanchez", state.header?.name, "TEST-CONTRACT-002: the header comes from the decoded fixture")
            assertEquals(
                "C-137",
                state.dimension,
                "TEST-CONTRACT-002: the dimension is the parenthesised designation of the fixture's origin",
            )
            assertEquals(
                listOf(CopyKeys.DETAIL_INFO_ORIGIN, CopyKeys.DETAIL_INFO_LAST_KNOWN_LOCATION),
                state.info.map { it.copyKey },
                "TEST-CONTRACT-002: with no enrichment the two independent rows render",
            )
            assertEquals(
                listOf("Earth (C-137)", "Citadel of Ricks").map(DisplayText::Data),
                state.info.map { it.value },
                "TEST-CONTRACT-002: the row values are the fixture's origin and location names",
            )
            assertTrue(
                state.info.any { it.kind == InfoRowKind.Origin },
                "TEST-CONTRACT-002: the fixture's origin is a real location, so the row is present rather than omitted",
            )
        }
}

package io.github.davidru85.multiverse.core.diagnostics

import io.github.davidru85.multiverse.core.data.cache.CachePolicy
import io.github.davidru85.multiverse.core.data.cache.ResponseCache
import io.github.davidru85.multiverse.core.data.logging.ValidatingAppLogger
import io.github.davidru85.multiverse.core.data.paging.RepositoryCharacterPager
import io.github.davidru85.multiverse.core.data.remote.rest.RestCharacterRemoteDataSource
import io.github.davidru85.multiverse.core.data.remote.rickAndMortyDefaults
import io.github.davidru85.multiverse.core.data.repository.RemoteCharacterRepository
import io.github.davidru85.multiverse.core.domain.logging.ErrorClass
import io.github.davidru85.multiverse.core.domain.logging.StatusFamily
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.result.DataSource
import io.github.davidru85.multiverse.testing.FixedRandom
import io.github.davidru85.multiverse.testing.MockHttp
import io.github.davidru85.multiverse.testing.MutableFakeClock
import io.github.davidru85.multiverse.testing.NoCacheStorage
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-033` — the debug-only diagnostic API (`REQ-OBS-002`, `OBSERVABILITY.md` §5, `DEC-085`,
 * `DEC-088`): a read-only fold over validated log records that exposes the last failure and the current
 * data source, proved on the real request and pager paths. Values that later integrations own are
 * explicitly unavailable, never an invented zero or a fabricated screen. Its absence from release is
 * proved on the module graph by `DiagnosticsBoundaryTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsRecorderTest {
    private class Stack(
        val recorder: DiagnosticsRecorder,
        val pager: RepositoryCharacterPager,
        val served: List<MockHttp.Served>,
    )

    /** The production stack, from the engine to the pager, writing to the recorder at debug level. */
    private fun TestScope.stack(vararg routes: MockHttp.Route): Stack {
        val recorder = DiagnosticsRecorder()
        val logger = ValidatingAppLogger.forDebug(recorder)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (raw, served) = MockHttp.sequence(*routes, dispatcher = dispatcher)
        val remote =
            RestCharacterRemoteDataSource(
                raw.config { rickAndMortyDefaults() },
                dispatcher,
                MutableFakeClock(),
                logger,
                testScheduler.timeSource,
            )
        // A store that retains nothing: this suite is about the records a request produces, and a real
        // store would stop a repeat load from reaching the network at all.
        val repository =
            RemoteCharacterRepository(
                remote,
                backgroundScope,
                FixedRandom(0.5),
                logger,
                ResponseCache(NoCacheStorage, MutableFakeClock(), CachePolicy(), logger),
            )
        return Stack(recorder, RepositoryCharacterPager(repository, this, logger, timeSource = testScheduler.timeSource), served)
    }

    @Test
    fun `TEST-UNIT-033 given_no_record_yet_then_each_value_is_not_yet_observed_or_explicitly_unavailable`() {
        val snapshot = DiagnosticsRecorder().snapshot.value

        assertEquals(Diagnostic.NotYetObserved, snapshot.lastFailure)
        assertEquals(Diagnostic.NotYetObserved, snapshot.currentSource)
        assertEquals(Diagnostic.NotYetObserved, snapshot.lastRequest)
        assertEquals(Diagnostic.NotYetObserved, snapshot.pager)
        listOf(snapshot.stale, snapshot.cacheState, snapshot.favoritesCount, snapshot.buildEnvelope).forEach { value ->
            val unavailable = assertIs<Diagnostic.Unavailable>(value, "TEST-UNIT-033: no emitter exists yet, so nothing is invented")
            assertTrue(unavailable.deliveredBy.isNotBlank(), "TEST-UNIT-033: and the owner of the value is named")
        }
    }

    @Test
    fun `TEST-UNIT-033 given_a_page_loaded_on_the_real_path_then_the_source_timing_and_pager_are_observed_without_a_request`() =
        TestTime.run {
            val s = stack(MockHttp.route("character-page-01.json"))

            s.pager.setFilter(CharacterFilter(query = "Rick"))
            val snapshot = s.recorder.snapshot.value

            assertEquals(Diagnostic.Observed(DataSource.NETWORK), snapshot.currentSource, "TEST-UNIT-033: the current data source")
            assertEquals(
                Diagnostic.Observed(RequestDiagnostic(durationMs = 0, statusFamily = StatusFamily.SUCCESSFUL)),
                snapshot.lastRequest,
            )
            assertEquals(Diagnostic.Observed(PagerDiagnostic(page = 1, hasNextPage = true)), snapshot.pager)
            assertEquals(Diagnostic.NotYetObserved, snapshot.lastFailure)
            assertEquals(1, s.served.size, "TEST-UNIT-033: reading the surface triggers no request")
        }

    @Test
    fun `TEST-UNIT-033 given_a_failure_on_the_real_path_then_the_last_failure_is_its_class_and_the_screen_is_unavailable`() =
        TestTime.run {
            val s =
                stack(
                    MockHttp.errorRoute("character-page-01.json", 503),
                    MockHttp.errorRoute("character-page-01.json", 503),
                    MockHttp.errorRoute("character-page-01.json", 503),
                )

            s.pager.setFilter(CharacterFilter())
            val snapshot = s.recorder.snapshot.value

            val failure = assertIs<Diagnostic.Observed<FailureDiagnostic>>(snapshot.lastFailure).value
            assertEquals(ErrorClass.SERVER, failure.errorClass, "TEST-UNIT-033: the failure type, never its message")
            assertIs<Diagnostic.Unavailable>(failure.screen, "TEST-UNIT-033: the data layer does not know the screen; none is fabricated")
            assertEquals(
                Diagnostic.Observed(RequestDiagnostic(durationMs = 0, statusFamily = StatusFamily.SERVER_ERROR)),
                snapshot.lastRequest,
            )
            assertEquals(Diagnostic.NotYetObserved, snapshot.currentSource, "TEST-UNIT-033: a failure is not a data source")
        }

    @Test
    fun `TEST-UNIT-033 given_the_last_page_then_the_pager_reports_no_next_page`() =
        TestTime.run {
            val s = stack(MockHttp.route("character-page-42.json"))

            s.pager.setFilter(CharacterFilter())

            assertEquals(Diagnostic.Observed(PagerDiagnostic(page = 1, hasNextPage = false)), s.recorder.snapshot.value.pager)
        }
}

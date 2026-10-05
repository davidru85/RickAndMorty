package io.github.davidru85.multiverse.feature.settings.presentation

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.usecase.ObserveFavoriteIds
import io.github.davidru85.multiverse.feature.settings.domain.ClearFavorites
import io.github.davidru85.multiverse.feature.settings.domain.ObserveAppSettings
import io.github.davidru85.multiverse.feature.settings.domain.UpdateAppSettings
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * `TEST-UNIT-050` and the Settings half of `TEST-UNIT-046` — the shared Settings state holder
 * (`IC-023`, `REQ-FUNC-033`…`REQ-FUNC-035`).
 *
 * The holder owns every rule a platform holder must not re-derive (`CONTRACTS.md` §7): the delete
 * confirmation opens only while favourites exist, `Delete` runs `ClearFavorites` exactly once and
 * closes it, `Cancel` and dismissal invoke nothing, and selecting the protocol already in force
 * reaches no write. The Sounds and protocol fields are the `IC-021` emission and nothing else, so a
 * new emission is mirrored without a second copy and without a write.
 *
 * Time is virtual throughout (`TESTING.md` §5): the holder runs on a `TestDispatcher` whose clock
 * only advances when the case says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStateHolderTest {
    private val character = CharacterId("1")

    private fun TestScope.holder(
        settings: FakeAppSettingsRepository = FakeAppSettingsRepository(),
        favorites: FakeFavoritesRepository = FakeFavoritesRepository(),
    ) = SettingsStateHolder(
        observeAppSettings = ObserveAppSettings(settings),
        updateAppSettings = UpdateAppSettings(settings),
        clearFavorites = ClearFavorites(favorites),
        observeFavoriteIds = ObserveFavoriteIds(favorites),
        scope = backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `TEST-UNIT-050 given_no_favorites_when_delete_is_requested_then_no_confirmation_opens`() =
        TestTime.run {
            val favorites = FakeFavoritesRepository()
            val holder = holder(favorites = favorites)
            holder.start()
            testScheduler.advanceTimeBy(1.milliseconds)

            assertFalse(holder.state.value.canDeleteFavorites, "TEST-UNIT-050: an empty set disables the action")

            holder.onIntent(SettingsIntent.DeleteFavoritesRequested)
            testScheduler.advanceTimeBy(1.milliseconds)

            assertFalse(
                holder.state.value.isConfirmingDelete,
                "TEST-UNIT-050: the confirmation opens only when canDeleteFavorites is true (AC-REQ-FUNC-035-3)",
            )
            assertEquals(0, favorites.clears, "TEST-UNIT-050: requesting the confirmation invokes nothing")
        }

    @Test
    fun `TEST-UNIT-050 given_favorites_when_delete_is_requested_then_the_confirmation_opens_and_nothing_is_cleared`() =
        TestTime.run {
            val favorites = FakeFavoritesRepository()
            favorites.mark(character)
            val holder = holder(favorites = favorites)
            holder.start()
            testScheduler.advanceTimeBy(1.milliseconds)

            assertTrue(holder.state.value.canDeleteFavorites, "TEST-UNIT-050: a non-empty set enables the action")

            holder.onIntent(SettingsIntent.DeleteFavoritesRequested)
            testScheduler.advanceTimeBy(1.milliseconds)

            assertTrue(holder.state.value.isConfirmingDelete, "TEST-UNIT-050: the request opens the confirmation")
            assertEquals(0, favorites.clears, "TEST-UNIT-050: opening the confirmation deletes nothing")
            assertEquals(setOf(character), favorites.ids, "TEST-UNIT-050: the stored set is untouched")
        }

    @Test
    fun `TEST-UNIT-050 given_the_confirmation_open_when_confirmed_then_clear_runs_exactly_once_and_the_confirmation_closes`() =
        TestTime.run {
            val favorites = FakeFavoritesRepository()
            favorites.mark(character)
            val holder = holder(favorites = favorites)
            holder.start()
            // The observation must have delivered before the request: the action is enabled by the
            // observed set, not by the store's contents (`AC-REQ-FUNC-035-3`).
            testScheduler.advanceTimeBy(1.milliseconds)
            assertTrue(holder.state.value.canDeleteFavorites, "TEST-UNIT-050: the case starts with favorites present")

            holder.onIntent(SettingsIntent.DeleteFavoritesRequested)
            testScheduler.advanceTimeBy(1.milliseconds)

            holder.onIntent(SettingsIntent.DeleteFavoritesConfirmed)
            testScheduler.advanceTimeBy(1.milliseconds)

            assertEquals(1, favorites.clears, "TEST-UNIT-050: Delete invokes ClearFavorites exactly once")
            assertTrue(favorites.ids.isEmpty(), "TEST-UNIT-050: one clear empties the stored set (AC-REQ-FUNC-035-2)")
            assertFalse(holder.state.value.isConfirmingDelete, "TEST-UNIT-050: the confirmation then closes")
            assertFalse(holder.state.value.canDeleteFavorites, "TEST-UNIT-050: the emptied set disables the action")
        }

    @Test
    fun `TEST-UNIT-050 given_the_confirmation_open_when_delete_is_tapped_twice_before_the_clear_runs_then_it_clears_once`() =
        TestTime.run {
            val favorites = FakeFavoritesRepository()
            favorites.mark(character)
            val holder = holder(favorites = favorites)
            holder.start()
            testScheduler.advanceTimeBy(1.milliseconds)
            holder.onIntent(SettingsIntent.DeleteFavoritesRequested)
            testScheduler.advanceTimeBy(1.milliseconds)

            // A double tap: both confirmations arrive before the first clear has had a chance to run,
            // so the open confirmation is the only guard between them (`TASK-111`).
            holder.onIntent(SettingsIntent.DeleteFavoritesConfirmed)
            holder.onIntent(SettingsIntent.DeleteFavoritesConfirmed)
            testScheduler.advanceTimeBy(1.milliseconds)

            assertEquals(1, favorites.clears, "TEST-UNIT-050: a re-entrant Delete must not clear the set a second time")
            assertFalse(holder.state.value.isConfirmingDelete, "TEST-UNIT-050: the confirmation is closed")
        }

    @Test
    fun `TEST-UNIT-050 given_the_confirmation_open_when_dismissed_then_nothing_is_invoked`() =
        TestTime.run {
            val favorites = FakeFavoritesRepository()
            favorites.mark(character)
            val holder = holder(favorites = favorites)
            holder.start()
            testScheduler.advanceTimeBy(1.milliseconds)
            assertTrue(holder.state.value.canDeleteFavorites, "TEST-UNIT-050: the case starts with favorites present")

            holder.onIntent(SettingsIntent.DeleteFavoritesRequested)
            testScheduler.advanceTimeBy(1.milliseconds)

            holder.onIntent(SettingsIntent.DeleteFavoritesDismissed)
            testScheduler.advanceTimeBy(1.milliseconds)

            assertEquals(0, favorites.clears, "TEST-UNIT-050: Cancel invokes nothing (AC-REQ-FUNC-035-1)")
            assertEquals(setOf(character), favorites.ids, "TEST-UNIT-050: Cancel changes nothing")
            assertFalse(holder.state.value.isConfirmingDelete, "TEST-UNIT-050: Cancel closes the confirmation")
        }

    @Test
    fun `TEST-UNIT-050 given_the_protocol_already_in_force_when_it_is_selected_then_the_seam_is_never_reached`() =
        TestTime.run {
            val settings = FakeAppSettingsRepository()
            val holder = holder(settings = settings)
            holder.start()
            testScheduler.advanceTimeBy(1.milliseconds)

            holder.onIntent(SettingsIntent.RemoteProtocolSelected(RemoteProtocol.Rest))
            testScheduler.advanceTimeBy(1.milliseconds)

            assertEquals(0, settings.updates, "TEST-UNIT-050: the current protocol writes nothing")
            assertEquals(0, settings.writes, "TEST-UNIT-050: and reaches no store write")
            assertEquals(RemoteProtocol.Rest, holder.state.value.remoteProtocol, "TEST-UNIT-050: REST stays selected")
        }

    @Test
    fun `TEST-UNIT-050 given_the_other_protocol_when_selected_then_it_is_persisted_once_and_mirrored`() =
        TestTime.run {
            val settings = FakeAppSettingsRepository()
            val holder = holder(settings = settings)
            holder.start()
            testScheduler.advanceTimeBy(1.milliseconds)

            holder.onIntent(SettingsIntent.RemoteProtocolSelected(RemoteProtocol.GraphQl))
            testScheduler.advanceTimeBy(1.milliseconds)

            assertEquals(1, settings.updates, "TEST-UNIT-050: a real change reaches the seam once")
            assertEquals(1, settings.writes, "TEST-UNIT-050: and is written once")
            assertEquals(RemoteProtocol.GraphQl, settings.settings.remoteProtocol, "TEST-UNIT-050: the store holds it")
            assertEquals(
                RemoteProtocol.GraphQl,
                holder.state.value.remoteProtocol,
                "TEST-UNIT-050: the state mirrors the emission that followed",
            )
        }

    @Test
    fun `TEST-UNIT-050 given_the_sounds_switch_when_toggled_then_the_preference_is_persisted_and_mirrored`() =
        TestTime.run {
            val settings = FakeAppSettingsRepository()
            val holder = holder(settings = settings)
            holder.start()
            testScheduler.advanceTimeBy(1.milliseconds)

            holder.onIntent(SettingsIntent.SoundsToggled(true))
            testScheduler.advanceTimeBy(1.milliseconds)

            assertTrue(settings.settings.soundsEnabled, "TEST-UNIT-050: a toggle reaches the store")
            assertTrue(holder.state.value.soundsEnabled, "TEST-UNIT-050: the state mirrors the emission")
        }

    @Test
    fun `TEST-UNIT-046 given_a_new_IC_021_emission_when_it_arrives_then_the_state_mirrors_it_without_a_write`() =
        TestTime.run {
            val settings = FakeAppSettingsRepository()
            val holder = holder(settings = settings)
            holder.start()
            testScheduler.advanceTimeBy(1.milliseconds)

            settings.emit(AppSettings(soundsEnabled = true, remoteProtocol = RemoteProtocol.GraphQl))
            testScheduler.advanceTimeBy(1.milliseconds)

            val mirrored = holder.state.value
            assertTrue(mirrored.soundsEnabled, "TEST-UNIT-046: a new emission updates soundsEnabled")
            assertEquals(
                RemoteProtocol.GraphQl,
                mirrored.remoteProtocol,
                "TEST-UNIT-046: a new emission updates remoteProtocol",
            )
            assertEquals(0, settings.updates, "TEST-UNIT-046: mirroring an emission is not a write")

            settings.emit(AppSettings())
            testScheduler.advanceTimeBy(1.milliseconds)

            assertEquals(
                SettingsUiState(),
                holder.state.value,
                "TEST-UNIT-046: the state holds no competing copy — the latest emission is the whole truth",
            )
        }

    @Test
    fun `TEST-UNIT-046 given_favorites_when_another_writer_empties_the_set_then_the_action_becomes_disabled`() =
        TestTime.run {
            val favorites = FakeFavoritesRepository()
            favorites.mark(character)
            val holder = holder(favorites = favorites)
            holder.start()
            testScheduler.advanceTimeBy(1.milliseconds)

            assertTrue(holder.state.value.canDeleteFavorites, "TEST-UNIT-046: the non-empty set enables the action")

            favorites.store.clear()
            testScheduler.advanceTimeBy(1.milliseconds)

            assertFalse(
                holder.state.value.canDeleteFavorites,
                "TEST-UNIT-046: canDeleteFavorites is true iff the observed set is non-empty (AC-REQ-FUNC-035-3)",
            )
        }
}

package io.github.davidru85.multiverse.core.data.settings

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.testing.TestTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-046` — the settings store's own semantics (`IC-021`, `IC-022`, `TASK-074`).
 *
 * The contract is stated once and satisfied by both platform `actual`s (`TESTING.md` §6.2), so this
 * suite drives the real store the target under test ships. It asserts the fresh-install defaults, the
 * restart durability, the atomic replace and the stable protocol encoding — every rule `IC-022` names
 * that a store can decide.
 */
class AppSettingsTest {
    private val storage = InMemorySettingsStorage()

    private fun repository() = LocalAppSettingsRepository(storage)

    @Test
    fun `TEST-UNIT-046 given_a_fresh_install_when_the_settings_are_observed_then_sounds_is_off_and_rest_is_selected`() =
        TestTime.run {
            val settings = repository().current()

            assertEquals(AppSettings(), settings, "the domain defaults ARE the fresh-install values (AC-REQ-FUNC-033-2)")
            assertEquals(false, settings.soundsEnabled)
            assertEquals(RemoteProtocol.Rest, settings.remoteProtocol, "AC-REQ-FUNC-034-1")
        }

    @Test
    fun `TEST-UNIT-046 given_a_written_change_when_the_store_is_reopened_then_the_value_survives_a_restart`() =
        TestTime.run {
            repository().update { it.copy(soundsEnabled = true, remoteProtocol = RemoteProtocol.GraphQl) }

            // A second repository over the same storage is what a process restart looks like.
            assertEquals(
                AppSettings(soundsEnabled = true, remoteProtocol = RemoteProtocol.GraphQl),
                LocalAppSettingsRepository(storage).current(),
            )
        }

    @Test
    fun `TEST-UNIT-046 given_an_unchanged_value_when_it_is_written_then_nothing_is_written`() =
        TestTime.run {
            val repository = repository()
            repository.current()

            repository.update { it }

            assertEquals(1L, storage.writes.toLong(), "an equal value is neither written nor emitted (IC-021)")
        }

    @Test
    fun `TEST-UNIT-046 given_an_unknown_stored_protocol_when_it_is_read_then_it_is_rest`() =
        TestTime.run {
            storage.sounds = true
            storage.protocol = "quux"

            assertEquals(
                AppSettings(soundsEnabled = true, remoteProtocol = RemoteProtocol.Rest),
                repository().current(),
                "an unknown string reads as the default, never as an error (IC-022)",
            )
        }

    @Test
    fun `TEST-UNIT-046 given_a_restart_when_sounds_was_never_touched_then_it_is_still_off`() =
        TestTime.run {
            repository().update { it.copy(remoteProtocol = RemoteProtocol.GraphQl) }

            val after = LocalAppSettingsRepository(storage).current()

            assertEquals(false, after.soundsEnabled, "an untouched field keeps its fresh-install value")
            assertEquals(RemoteProtocol.GraphQl, after.remoteProtocol, "and the changed one persists")
        }
}

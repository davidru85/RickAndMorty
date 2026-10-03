package io.github.davidru85.multiverse.core.data.settings

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.testing.TestTime
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-046` — the repository's own contract (`IC-021`, `TASK-074`).
 *
 * `IC-021` states that `observe()` "emits the current value to every new collector first, then each
 * distinct change". A consumer that reads the protocol from the flow — the per-request selection of
 * `IC-011` — therefore must not also have to reach past the repository to the store. These cases pin
 * that, which is what lets one class own the settings.
 */
class LocalAppSettingsRepositoryTest {
    private val storage = InMemorySettingsStorage()

    @Test
    fun `TEST-UNIT-046 given_a_persisted_value_when_a_cold_collector_observes_then_it_receives_it_without_a_previous_emission`() =
        TestTime.run {
            storage.seed(soundsEnabled = true, remoteProtocol = "graphql")

            assertEquals(
                AppSettings(soundsEnabled = true, remoteProtocol = RemoteProtocol.GraphQl),
                LocalAppSettingsRepository(storage).observe().first(),
                "a new collector is seeded from the store, never left waiting for a change (IC-021)",
            )
        }

    @Test
    fun `TEST-UNIT-046 given_a_seeded_snapshot_when_a_change_is_written_then_the_next_emission_is_the_new_value`() =
        TestTime.run {
            val repository = LocalAppSettingsRepository(storage)

            assertEquals(AppSettings(), repository.observe().first(), "the seeded value comes first")
            repository.update { it.copy(soundsEnabled = true) }

            assertEquals(true, repository.observe().first().soundsEnabled, "and the change follows it")
        }
}

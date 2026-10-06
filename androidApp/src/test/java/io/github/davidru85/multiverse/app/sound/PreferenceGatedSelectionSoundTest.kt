package io.github.davidru85.multiverse.app.sound

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.davidru85.multiverse.app.di.shellModules
import io.github.davidru85.multiverse.core.data.di.coreModule
import io.github.davidru85.multiverse.core.data.settings.LocalAppSettingsRepository
import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.testing.FakeAppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `TEST-UNIT-109` — the selection sound plays only while Sounds is on (`REQ-FUNC-036`,
 * `AC-REQ-FUNC-036-3`, `TASK-139`, `DEC-162`).
 *
 * The gate reads the preference through the real `IC-021` repository over an in-memory store, so the
 * fresh-install default, a value restored at start-up and a change made in Settings are the values the
 * app would read. A change applies from the next tap, with no restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PreferenceGatedSelectionSoundTest {
    @Test
    fun `TEST-UNIT-109 given_a_fresh_install_when_the_user_selects_then_nothing_plays_until_sounds_is_turned_on`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = LocalAppSettingsRepository(FakeAppSettingsStore())
            var plays = 0
            val sound = PreferenceGatedSelectionSound(repository, backgroundScope) { plays++ }
            runCurrent()

            sound.play()
            assertEquals("TEST-UNIT-109: Sounds is off on a fresh install, so nothing plays", 0, plays)

            repository.update { it.copy(soundsEnabled = true) }
            runCurrent()
            sound.play()
            assertEquals("TEST-UNIT-109: turned on in Settings, the next tap plays", 1, plays)

            repository.update { it.copy(soundsEnabled = false) }
            runCurrent()
            sound.play()
            assertEquals("TEST-UNIT-109: turned off again, the next tap plays nothing", 1, plays)
        }

    @Test
    fun `TEST-UNIT-109 given_sounds_stored_on_when_the_app_starts_then_the_first_selection_plays`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = LocalAppSettingsRepository(FakeAppSettingsStore(AppSettings(soundsEnabled = true)))
            var plays = 0
            val sound = PreferenceGatedSelectionSound(repository, backgroundScope) { plays++ }
            runCurrent()

            sound.play()

            assertEquals("TEST-UNIT-109: a stored preference is honoured from the first tap", 1, plays)
        }

    @Test
    fun `TEST-UNIT-109 given_the_shell_graph_when_the_sound_is_resolved_then_it_is_the_preference_gated_bundled_sound`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val koin =
            koinApplication {
                androidContext(context)
                modules(coreModule)
                modules(shellModules(CoroutineScope(Dispatchers.Unconfined), context))
            }.koin

        assertTrue(
            "TEST-UNIT-109: the shell binds one sound, gated by the preference",
            koin.get<SelectionSound>() is PreferenceGatedSelectionSound,
        )
    }
}

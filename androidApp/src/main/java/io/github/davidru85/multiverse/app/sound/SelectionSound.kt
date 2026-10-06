package io.github.davidru85.multiverse.app.sound

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.repository.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The selection sound of `REQ-FUNC-036` (`DEC-162`). The shell asks it to play on each selection the
 * user makes — a change of destination on the navigation bar, and every tap of the Discovery status
 * selector — and the implementation decides what plays, and whether.
 */
public fun interface SelectionSound {
    public fun play()
}

/**
 * Plays [sound] only while the Sounds preference is on (`REQ-FUNC-036`, `AC-REQ-FUNC-036-3`).
 *
 * The preference is observed from [scope] for as long as the scope lives, and it starts from the
 * fresh-install value, off (`AC-REQ-FUNC-033-2`), so a tap before the store has answered plays nothing.
 * A change made in Settings applies from the next tap, without a restart.
 */
public class PreferenceGatedSelectionSound(
    settings: AppSettingsRepository,
    scope: CoroutineScope,
    private val sound: SelectionSound,
) : SelectionSound {
    private val enabled: StateFlow<Boolean> =
        settings
            .observe()
            .map { it.soundsEnabled }
            .stateIn(scope, SharingStarted.Eagerly, AppSettings().soundsEnabled)

    override fun play() {
        if (enabled.value) sound.play()
    }
}

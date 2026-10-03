package io.github.davidru85.multiverse.core.data.settings

import io.github.davidru85.multiverse.core.domain.model.AppSettings

/** The behavioural double of `IC-022` (`DEC-072`): a store a case can seed and count. */
internal class InMemorySettingsStorage : AppSettingsLocalDataSource {
    var sounds: Boolean = false
    var protocol: String = "rest"

    /** How many writes reached the seam. */
    var writes: Int = 0
        private set

    override suspend fun read(): AppSettings = AppSettings()

    override suspend fun write(settings: AppSettings) {
        writes++
    }
}

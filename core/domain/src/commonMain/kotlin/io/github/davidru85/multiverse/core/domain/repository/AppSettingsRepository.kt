package io.github.davidru85.multiverse.core.domain.repository

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * The user's settings (`IC-021`). [observe] emits the current value first, then each distinct
 * change; [update] applies its function to the latest persisted value and writes the result
 * atomically. Implemented by `TASK-074`.
 */
public interface AppSettingsRepository {
    public fun observe(): Flow<AppSettings>

    public suspend fun update(change: (AppSettings) -> AppSettings)
}

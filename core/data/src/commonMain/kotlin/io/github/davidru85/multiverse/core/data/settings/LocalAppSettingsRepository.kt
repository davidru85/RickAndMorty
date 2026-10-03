package io.github.davidru85.multiverse.core.data.settings

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow

/** Stub: the repository `IC-021` declares, with no behaviour behind it yet. */
public class LocalAppSettingsRepository(
    private val local: AppSettingsLocalDataSource,
) : AppSettingsRepository {
    override fun observe(): Flow<AppSettings> = kotlinx.coroutines.flow.flow { }

    override suspend fun update(change: (AppSettings) -> AppSettings): Unit = Unit

    /** The current value, for a caller that has no flow collection yet. */
    public suspend fun current(): AppSettings = local.read()
}

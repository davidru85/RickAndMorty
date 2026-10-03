package io.github.davidru85.multiverse.feature.settings.domain

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * The settings the user configured (`IC-009`, `CONTRACTS.md` §6 use-case table) — the one source the
 * Settings state holder renders (`IC-023`).
 *
 * It is the `IC-021` observation and nothing else: the emitted flow is hot, conflated and seeded from
 * the store on first collection, so the state holder needs no second read path. The class is
 * stateless, reads no clock and no platform API.
 */
public class ObserveAppSettings(
    private val repository: AppSettingsRepository,
) {
    /** The current settings, then each distinct change. */
    public operator fun invoke(): Flow<AppSettings> = repository.observe()
}

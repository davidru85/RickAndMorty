package io.github.davidru85.multiverse.feature.settings.domain

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.repository.AppSettingsRepository

/**
 * A change to the user's settings (`IC-009`, `CONTRACTS.md` §6 use-case table; `REQ-FUNC-033`,
 * `REQ-FUNC-034`).
 *
 * It is the `IC-021` write and nothing else: the repository applies [change] to the latest persisted
 * value, serialises concurrent updates and writes nothing when the result equals what was there, so
 * a selection of the protocol already in force reaches the store as no write at all
 * (`IC-023`'s no-op rule).
 */
public class UpdateAppSettings(
    private val repository: AppSettingsRepository,
) {
    /** Applies [change] to the stored settings. */
    public suspend operator fun invoke(change: (AppSettings) -> AppSettings): Unit = repository.update(change)
}

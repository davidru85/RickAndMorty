package io.github.davidru85.multiverse.core.data.settings

import io.github.davidru85.multiverse.core.domain.model.AppSettings

/**
 * The storage seam of the app's settings (`IC-022`): DataStore on Android, `UserDefaults` on Apple,
 * one `actual` per target behind this interface and behind the same contract (`DEC-017`).
 *
 * It stores the two [AppSettings] fields and nothing else, shares no key with the favourites store
 * (`IC-013`), and reads a missing or unreadable value as the `IC-021` default for that field rather
 * than as an error. The protocol is persisted by a stable string, never by ordinal, so an enum
 * reorder cannot silently change a stored choice.
 */
public interface AppSettingsLocalDataSource {
    public suspend fun read(): AppSettings

    public suspend fun write(settings: AppSettings)
}

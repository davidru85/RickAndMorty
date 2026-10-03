package io.github.davidru85.multiverse.testing

import io.github.davidru85.multiverse.core.data.settings.AppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol

/**
 * The behavioural double of `IC-022` (`DEC-072`), for a suite that needs a settings store without a
 * file or a defaults database (`TASK-074`).
 *
 * It persists what it is given and counts the writes, so the graph resolves and a case can assert the
 * equal-value rule. The store's own semantics are proved against the real platform stores by
 * `TEST-UNIT-046`.
 */
public class FakeAppSettingsStore(
    initial: AppSettings = AppSettings(),
) : AppSettingsLocalDataSource {
    private var current = initial

    /** How many writes reached the seam. */
    public var writes: Int = 0
        private set

    /** The value the next read returns. */
    public val value: AppSettings get() = current

    override suspend fun read(): AppSettings = current

    override suspend fun write(settings: AppSettings) {
        writes++
        current = settings
    }

    /** The value as a fresh install would hold it, for a case that wants the defaults back. */
    public fun reset() {
        current = AppSettings()
        writes = 0
    }

    /** The protocol the store currently holds; a helper for a selection case. */
    public fun protocol(): RemoteProtocol = current.remoteProtocol
}

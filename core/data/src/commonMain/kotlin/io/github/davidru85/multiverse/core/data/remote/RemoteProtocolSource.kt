package io.github.davidru85.multiverse.core.data.remote

import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The active remote protocol, read **per request** (`IC-021`, `REQ-FUNC-034`, `DEC-056`,
 * [`adr/0011-runtime-remote-protocol.md`](../../../../../../../../docs/adr/0011-runtime-remote-protocol.md)).
 *
 * This is the seam that keeps "which protocol" out of every caller: the repository asks [current] for
 * each request, so the stored preference decides the adapter without a screen, a use case or a state
 * holder knowing which one answered. [changes] is what makes the choice an identity change for the
 * pager: a new value cancels the load in flight, resets the list to page 1 and reloads through the
 * newly selected adapter (`AC-REQ-FUNC-034-2`).
 *
 * The default, [Rest], is a fresh install's choice, so a graph that selects nothing cannot silently
 * reach the other adapter (`AC-REQ-FUNC-034-1`).
 */
public interface RemoteProtocolSource {
    /** The protocol the next request must use. Suspends because reading the preference may. */
    public suspend fun current(): RemoteProtocol

    /** Every change of the active protocol, in order; the default never changes. */
    public fun changes(): Flow<RemoteProtocol>

    public companion object {
        /** A fresh install's choice: REST, with no change to observe (`AC-REQ-FUNC-034-1`). */
        public val Rest: RemoteProtocolSource =
            object : RemoteProtocolSource {
                override suspend fun current(): RemoteProtocol = RemoteProtocol.Rest

                override fun changes(): Flow<RemoteProtocol> = emptyFlow()
            }
    }
}

/**
 * The [RemoteProtocolSource] over `IC-021` (`TASK-074`, `TASK-075`).
 *
 * One class owns the settings, so this selector has no second path to the value: [current] takes the
 * first value `IC-021`'s `observe` emits, which its contract fixes as the persisted value to every new
 * collector, and [changes] is the same flow without the seeding read. `IC-021`'s `update` is the only
 * writer, so the flow and the store cannot disagree.
 */
public fun settingsProtocolSource(settings: AppSettingsRepository): RemoteProtocolSource =
    object : RemoteProtocolSource {
        override suspend fun current(): RemoteProtocol = settings.observe().first().remoteProtocol

        override fun changes(): Flow<RemoteProtocol> =
            settings
                .observe()
                .map { it.remoteProtocol }
                .distinctUntilChanged()
    }

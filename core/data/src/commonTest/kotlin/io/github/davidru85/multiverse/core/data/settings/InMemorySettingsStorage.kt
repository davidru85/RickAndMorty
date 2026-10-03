package io.github.davidru85.multiverse.core.data.settings

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol

/**
 * The behavioural double of `IC-022` (`DEC-072`): a store that persists what it is given and counts
 * the writes.
 *
 * It holds the two fields the way the platform stores do — the boolean as itself and the protocol as
 * its stable string (`IC-022`) — so a case can seed an unrecognised stored value and observe that the
 * read degrades to the default rather than to an error.
 */
internal class InMemorySettingsStorage : AppSettingsLocalDataSource {
    private var sounds: Boolean = false
    private var protocol: String = "rest"

    /** How many writes reached the seam. */
    var writes: Int = 0
        private set

    /** Seeds the persisted values directly, which is what a foreign process or an old build leaves. */
    fun seed(
        soundsEnabled: Boolean = false,
        remoteProtocol: String = "rest",
    ) {
        sounds = soundsEnabled
        protocol = remoteProtocol
    }

    override suspend fun read(): AppSettings = AppSettings(soundsEnabled = sounds, remoteProtocol = protocolFromWire(protocol))

    override suspend fun write(settings: AppSettings) {
        writes++
        sounds = settings.soundsEnabled
        protocol = settings.remoteProtocol.wireName
    }
}

/** The stable stored form of a protocol (`IC-022`): never an ordinal, never a display name. */
internal val RemoteProtocol.wireName: String
    get() =
        when (this) {
            RemoteProtocol.Rest -> "rest"
            RemoteProtocol.GraphQl -> "graphql"
        }

/** The protocol an unrecognised stored value reads as: the `IC-021` default, never an error. */
internal fun protocolFromWire(value: String): RemoteProtocol =
    when (value) {
        "graphql" -> RemoteProtocol.GraphQl
        else -> RemoteProtocol.Rest
    }

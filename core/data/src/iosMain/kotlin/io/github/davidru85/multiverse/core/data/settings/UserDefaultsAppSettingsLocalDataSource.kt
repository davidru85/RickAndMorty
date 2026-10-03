package io.github.davidru85.multiverse.core.data.settings

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import platform.Foundation.NSUserDefaults

/**
 * The Apple store of `IC-022` (`DEC-017`, ADR-0010): the same two settings in `UserDefaults`, the
 * technology the favourites store already uses and a different key namespace.
 *
 * It satisfies the one contract the Android store satisfies — the fresh-install defaults, the stable
 * protocol string, an unrecognised value reading as the default — so a behaviour stated once holds on
 * both targets (`TESTING.md` §6.2). `NSUserDefaults` persists across process restarts.
 */
public class UserDefaultsAppSettingsLocalDataSource(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AppSettingsLocalDataSource {
    override suspend fun read(): AppSettings =
        AppSettings(
            soundsEnabled =
                if (defaults.objectForKey(SOUNDS_ENABLED) ==
                    null
                ) {
                    AppSettings().soundsEnabled
                } else {
                    defaults.boolForKey(SOUNDS_ENABLED)
                },
            remoteProtocol = protocolFromWire(defaults.stringForKey(REMOTE_PROTOCOL)),
        )

    override suspend fun write(settings: AppSettings) {
        defaults.setBool(settings.soundsEnabled, SOUNDS_ENABLED)
        defaults.setObject(settings.remoteProtocol.wireName(), REMOTE_PROTOCOL)
    }

    private companion object {
        /** The two keys this store owns; the favourites' store keeps its own. */
        const val SOUNDS_ENABLED = "multiverse.settings.sounds_enabled"
        const val REMOTE_PROTOCOL = "multiverse.settings.remote_protocol"
    }
}

/** The stable stored form of a protocol (`IC-022`): never an ordinal, never a display name. */
internal fun RemoteProtocol.wireName(): String =
    when (this) {
        RemoteProtocol.Rest -> "rest"
        RemoteProtocol.GraphQl -> "graphql"
    }

/** The protocol an unrecognised or missing stored value reads as: the default, never an error. */
internal fun protocolFromWire(value: String?): RemoteProtocol =
    when (value) {
        "graphql" -> RemoteProtocol.GraphQl
        else -> RemoteProtocol.Rest
    }

package io.github.davidru85.multiverse.core.domain

import io.github.davidru85.multiverse.core.domain.model.AppSettings
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import kotlin.test.Test
import kotlin.test.assertEquals

/** `IC-021`: the declared defaults are the fresh-install values (`AC-REQ-FUNC-033-2`, `AC-REQ-FUNC-034-1`). */
class AppSettingsTest {
    @Test
    fun `TEST-UNIT-052 given_no_stored_value_when_settings_are_built_then_sounds_are_off_and_rest_is_selected`() {
        val settings = AppSettings()
        assertEquals(false, settings.soundsEnabled, "TEST-UNIT-052: Sounds off on a fresh install")
        assertEquals(RemoteProtocol.Rest, settings.remoteProtocol, "TEST-UNIT-052: REST is the default protocol")
        assertEquals(listOf(RemoteProtocol.Rest, RemoteProtocol.GraphQl), RemoteProtocol.entries, "TEST-UNIT-052")
    }
}

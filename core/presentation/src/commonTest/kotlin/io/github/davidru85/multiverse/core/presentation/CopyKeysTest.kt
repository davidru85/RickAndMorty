package io.github.davidru85.multiverse.core.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-036`, the registry half — the one canonical key list (`IC-017`, `DEC-015`, `DEC-020`):
 * every key the failure chain binds (`ERROR_FLOW.md` §4.1) and every key the formatters return is
 * registered once, under a resource-safe name. The resource half is `CopyParityTest`.
 */
class CopyKeysTest {
    @Test
    fun `TEST-UNIT-036 given_the_registry_then_it_holds_every_key_the_failure_chain_binds`() {
        val bound =
            listOf(
                "error_title",
                "action_retry",
                "state_stale_banner",
                "empty_search_message",
                "action_clear_filters",
                "action_back",
                "error_message_offline",
                "error_message_timeout",
                "error_message_not_found",
                "error_message_invalid_request",
                "error_message_rate_limited",
                "error_message_server",
                "error_message_graphql",
                "error_message_malformed",
                "error_message_empty_body",
                "error_message_unknown",
                "detail_error_inline",
            )

        assertTrue(CopyKeys.all.map { it.value }.containsAll(bound), "TEST-UNIT-036: ERROR_FLOW.md 4.1 is bound to registered keys")
        assertTrue(CopyKeys.all.containsAll(listOf(CopyKeys.STATUS_ALIVE, CopyKeys.STATUS_DEAD, CopyKeys.VALUE_UNKNOWN)))
    }

    @Test
    fun `TEST-UNIT-036 given_the_registry_then_every_name_is_unique_and_resource_safe_on_both_platforms`() {
        val names = CopyKeys.all.map { it.value }

        assertEquals(names.size, names.distinct().size, "TEST-UNIT-036: one key, one name")
        assertTrue(
            names.all { Regex("^[a-z][a-z0-9_]*$").matches(it) },
            "TEST-UNIT-036: a valid Android resource name and Apple key: $names",
        )
    }
}

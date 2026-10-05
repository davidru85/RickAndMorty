package io.github.davidru85.multiverse.core.data.remote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-071`'s shared half — the one host rule a portrait URL must pass before any layer of an
 * image pipeline touches it (`REQ-SEC-001`, `AC-REQ-SEC-001-1`, `DEC-126`, `TASK-111`).
 *
 * Android's Coil loader fetches through the allow-listed Ktor client; the iOS pipeline has no such
 * client, so it asks this predicate instead, and the rule exists once: HTTPS, the configured host
 * exactly, its default port and no user info. Every case is a string as a payload would carry it.
 */
class ImageUrlAllowListTest {
    @Test
    fun `TEST-UNIT-071 given_an_avatar_on_the_configured_host_when_checked_then_it_is_allowed`() {
        assertTrue(RickAndMortyApi.isAllowedImageUrl("https://rickandmortyapi.com/api/character/avatar/1.jpeg"))
        assertTrue(
            RickAndMortyApi.isAllowedImageUrl("https://rickandmortyapi.com:443/api/character/avatar/2.jpeg"),
            "TEST-UNIT-071: the default port, stated explicitly, is still the configured endpoint",
        )
    }

    @Test
    fun `TEST-UNIT-071 given_a_url_that_leaves_the_rule_when_checked_then_it_is_rejected`() {
        val rejected =
            listOf(
                "https://evil.example/avatar/1.jpeg",
                "http://rickandmortyapi.com/api/character/avatar/1.jpeg",
                "https://cdn.rickandmortyapi.com/api/character/avatar/1.jpeg",
                "https://rickandmortyapi.com.evil.example/api/character/avatar/1.jpeg",
                "https://rickandmortyapi.com:8443/api/character/avatar/1.jpeg",
                "https://user@rickandmortyapi.com/api/character/avatar/1.jpeg",
                "ftp://rickandmortyapi.com/api/character/avatar/1.jpeg",
                "not a url",
                "",
            )

        rejected.forEach { url ->
            assertFalse(RickAndMortyApi.isAllowedImageUrl(url), "TEST-UNIT-071: `$url` must be rejected before any fetch")
        }
    }
}

package io.github.davidru85.multiverse.core.designsystem.copy

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.davidru85.multiverse.core.designsystem.R

/**
 * The one Android copy resolver (`DEC-100`).
 *
 * Components take a copy **key name** (a `String` primitive) and never a presentation type, so
 * `:core:designsystem` keeps its Compose-only, project-dependency-free build while the strings stay
 * in one place. This table is the compile-checked mapping from a key name to its `R.string`
 * resource: a key added to `CopyKeys` without its string here does not compile, and a string added
 * here without a `CopyKeys` entry fails `TEST-UNIT-036`.
 *
 * The table is compared against the shipped `res/values/strings.xml` by
 * `CopyResolverTableTest`, so an entry cannot name a resource that does not exist.
 */
public object CopyResolver {
    /** The resource id for a canonical copy key name, or `null` when the name is not registered. */
    @StringRes
    public fun resourceId(key: String): Int? = TABLE[key]

    /**
     * Resolves a canonical copy key name against the device locale. An unregistered name fails
     * loudly in debug rather than rendering an empty string, because a missing key is a defect the
     * parity test exists to catch.
     */
    @Composable
    public fun copy(key: String): String {
        val id = TABLE[key]
        requireNotNull(id) { "no Android resource is registered for the copy key `$key` (DEC-100)" }
        return stringResource(id)
    }

    /** Every registered name, for the table ↔ resource parity test. */
    public fun names(): Set<String> = TABLE.keys

    private val TABLE: Map<String, Int> =
        buildMap {
            put("app_name", R.string.app_name)
            put("splash_wordmark", R.string.splash_wordmark)
            put("splash_wordmark_sub", R.string.splash_wordmark_sub)
            put("splash_tagline", R.string.splash_tagline)
            put("splash_loading", R.string.splash_loading)
            put("characters_count", R.string.characters_count)
            put("search_characters", R.string.search_characters)
            put("filter_all", R.string.filter_all)
            put("nav_characters", R.string.nav_characters)
            put("nav_episodes", R.string.nav_episodes)
            put("nav_favorites", R.string.nav_favorites)
            put("nav_settings", R.string.nav_settings)
            put("episodes_heading", R.string.episodes_heading)
            put("episodes_body", R.string.episodes_body)
            put("favorites_heading", R.string.favorites_heading)
            put("favorites_body", R.string.favorites_body)
            put("browse_characters", R.string.browse_characters)
            put("error_title", R.string.error_title)
            put("action_retry", R.string.action_retry)
            put("state_stale_banner", R.string.state_stale_banner)
            put("empty_search_message", R.string.empty_search_message)
            put("action_clear_filters", R.string.action_clear_filters)
            put("action_back", R.string.action_back)
            put("error_message_offline", R.string.error_message_offline)
            put("error_message_timeout", R.string.error_message_timeout)
            put("error_message_not_found", R.string.error_message_not_found)
            put("error_message_invalid_request", R.string.error_message_invalid_request)
            put("error_message_rate_limited", R.string.error_message_rate_limited)
            put("error_message_server", R.string.error_message_server)
            put("error_message_graphql", R.string.error_message_graphql)
            put("error_message_malformed", R.string.error_message_malformed)
            put("error_message_empty_body", R.string.error_message_empty_body)
            put("error_message_unknown", R.string.error_message_unknown)
            put("detail_error_inline", R.string.detail_error_inline)
            put("status_alive", R.string.status_alive)
            put("status_dead", R.string.status_dead)
            put("value_unknown", R.string.value_unknown)
        }
}

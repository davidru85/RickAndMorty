package io.github.davidru85.multiverse.core.presentation

import kotlin.jvm.JvmInline

/**
 * The name of a user-visible string (`IC-017`). The string itself lives in each platform's resource
 * files, in English and Spanish, and `TEST-UNIT-036` holds the two platforms identical per locale.
 */
@JvmInline
public value class CopyKey(
    public val value: String,
)

/**
 * The one canonical key list (`IC-017`, `DEC-015`, `DEC-020`). It registers the keys the shared
 * contracts bind — the failure chain of `ERROR_FLOW.md` §4.1 and the keys the formatters return — and
 * a feature registers its own keys here with the resources that carry them (`TASK-013`, `TASK-060`).
 * Platform resource files follow this list; no platform keeps a list of its own.
 */
public object CopyKeys {
    private val registered = mutableListOf<CopyKey>()

    private fun key(name: String): CopyKey = CopyKey(name).also { registered += it }

    // The failure chain (`ERROR_FLOW.md` §4.1).
    public val ERROR_TITLE: CopyKey = key("error_title")
    public val ACTION_RETRY: CopyKey = key("action_retry")
    public val STATE_STALE_BANNER: CopyKey = key("state_stale_banner")
    public val EMPTY_SEARCH_MESSAGE: CopyKey = key("empty_search_message")
    public val ACTION_CLEAR_FILTERS: CopyKey = key("action_clear_filters")
    public val ACTION_BACK: CopyKey = key("action_back")
    public val ERROR_MESSAGE_OFFLINE: CopyKey = key("error_message_offline")
    public val ERROR_MESSAGE_TIMEOUT: CopyKey = key("error_message_timeout")
    public val ERROR_MESSAGE_NOT_FOUND: CopyKey = key("error_message_not_found")
    public val ERROR_MESSAGE_INVALID_REQUEST: CopyKey = key("error_message_invalid_request")
    public val ERROR_MESSAGE_RATE_LIMITED: CopyKey = key("error_message_rate_limited")

    /** The rate-limit message when the server advised no usable countdown (`DEC-123`). */
    public val ERROR_MESSAGE_RATE_LIMITED_NO_COUNTDOWN: CopyKey = key("error_message_rate_limited_no_countdown")
    public val ERROR_MESSAGE_SERVER: CopyKey = key("error_message_server")
    public val ERROR_MESSAGE_GRAPHQL: CopyKey = key("error_message_graphql")
    public val ERROR_MESSAGE_MALFORMED: CopyKey = key("error_message_malformed")
    public val ERROR_MESSAGE_EMPTY_BODY: CopyKey = key("error_message_empty_body")
    public val ERROR_MESSAGE_UNKNOWN: CopyKey = key("error_message_unknown")
    public val DETAIL_ERROR_INLINE: CopyKey = key("detail_error_inline")

    // The formatters (`IC-017`): the status labels and the one "Unknown" presentation.
    public val STATUS_ALIVE: CopyKey = key("status_alive")
    public val STATUS_DEAD: CopyKey = key("status_dead")
    public val VALUE_UNKNOWN: CopyKey = key("value_unknown")

    // The gender labels (`IC-017`, `DEC-131`); an unknown or unrecognised gender is `value_unknown`.
    public val GENDER_FEMALE: CopyKey = key("gender_female")
    public val GENDER_MALE: CopyKey = key("gender_male")
    public val GENDER_GENDERLESS: CopyKey = key("gender_genderless")

    // The B4 surfaces (`TASK-013`, `DEC-100`, `DEC-101`): the app name, the splash, the navigation
    // and the two placeholder screens. Each key's English string is canonical in `UI_SPEC.md`
    // §6.1/§6.4 and is carried by the one Android copy set in `:core:designsystem`.
    public val APP_NAME: CopyKey = key("app_name")
    public val SPLASH_WORDMARK: CopyKey = key("splash_wordmark")
    public val SPLASH_WORDMARK_SUB: CopyKey = key("splash_wordmark_sub")
    public val SPLASH_TAGLINE: CopyKey = key("splash_tagline")
    public val SPLASH_LOADING: CopyKey = key("splash_loading")
    public val NAV_CHARACTERS: CopyKey = key("nav_characters")
    public val NAV_EPISODES: CopyKey = key("nav_episodes")
    public val NAV_FAVORITES: CopyKey = key("nav_favorites")
    public val NAV_SETTINGS: CopyKey = key("nav_settings")
    public val EPISODES_HEADING: CopyKey = key("episodes_heading")
    public val EPISODES_BODY: CopyKey = key("episodes_body")
    public val FAVORITES_HEADING: CopyKey = key("favorites_heading")
    public val FAVORITES_BODY: CopyKey = key("favorites_body")
    public val BROWSE_CHARACTERS: CopyKey = key("browse_characters")

    // The Discovery surface (`TASK-001`, `TASK-004`, `UI_SPEC.md` §6.2): the headline's count line,
    // the search field's placeholder and the first of the four filter options. The other three
    // options reuse `status_alive`, `status_dead` and `value_unknown`, which the spec already
    // words identically to the status treatment.
    public val CHARACTERS_COUNT: CopyKey = key("characters_count")
    public val SEARCH_CHARACTERS: CopyKey = key("search_characters")
    public val FILTER_ALL: CopyKey = key("filter_all")

    // The detail surface (`TASK-002`, `TASK-023`, `UI_SPEC.md` §6.3): the favourite action, the share
    // control, the three stat tiles and the three info-row labels.
    public val DETAIL_ACTION_FAVORITE: CopyKey = key("detail_action_favorite")
    public val ACTION_SHARE: CopyKey = key("action_share")

    /** The line the Detail's Share sends: the name, then the API resource URL (`DEC-125`). */
    public val SHARE_CHARACTER_TEXT: CopyKey = key("share_character_text")

    public val DETAIL_STAT_EPISODES: CopyKey = key("detail_stat_episodes")
    public val DETAIL_STAT_DIMENSION: CopyKey = key("detail_stat_dimension")
    public val DETAIL_STAT_SPECIES: CopyKey = key("detail_stat_species")
    public val DETAIL_INFO_ORIGIN: CopyKey = key("detail_info_origin")
    public val DETAIL_INFO_LAST_KNOWN_LOCATION: CopyKey = key("detail_info_last_known_location")
    public val DETAIL_INFO_FIRST_SEEN_IN: CopyKey = key("detail_info_first_seen_in")

    // The settings surface (`TASK-074`, `TASK-076`, `UI_SPEC.md` §6.5). The screen title reuses
    // `nav_settings`, the Favorites section header reuses `nav_favorites`, and the "Delete favorites"
    // disabled state reuses the button's own key.
    public val SETTINGS_SECTION_PREFERENCES: CopyKey = key("settings_section_preferences")
    public val SETTINGS_SECTION_DATA: CopyKey = key("settings_section_data")
    public val SETTINGS_SOUND_TITLE: CopyKey = key("settings_sound_title")
    public val SETTINGS_SOUND_BODY: CopyKey = key("settings_sound_body")
    public val SETTINGS_DATA_SOURCE_TITLE: CopyKey = key("settings_data_source_title")
    public val SETTINGS_DATA_REST: CopyKey = key("settings_data_rest")
    public val SETTINGS_DATA_GRAPHQL: CopyKey = key("settings_data_graphql")
    public val SETTINGS_DELETE_ACTION: CopyKey = key("settings_delete_action")
    public val SETTINGS_DELETE_EXPLANATION: CopyKey = key("settings_delete_explanation")
    public val SETTINGS_DELETE_CONFIRM_TITLE: CopyKey = key("settings_delete_confirm_title")
    public val SETTINGS_DELETE_CONFIRM_MESSAGE: CopyKey = key("settings_delete_confirm_message")
    public val ACTION_CANCEL: CopyKey = key("action_cancel")
    public val ACTION_DELETE: CopyKey = key("action_delete")

    /** Every registered key, in registration order. Declared last, so it sees every key above. */
    public val all: Set<CopyKey> = registered.toSet()
}

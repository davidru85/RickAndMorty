package io.github.davidru85.multiverse.core.domain.logging

/**
 * The closed value sets of `OBSERVABILITY.md` §2.2 that the events of this build carry. An event's
 * field is one of these, never a free-form string a call site fills in (`IC-024`); the protocol and the
 * cache source reuse `RemoteProtocol` and `DataSource`, which already are those sets.
 */
public enum class LogOperation { CHARACTER_LIST, CHARACTER_DETAIL, EPISODE_BATCH }

/** A path as a compile-time template: never an interpolated id, page number or query string. */
public enum class PathTemplate(
    public val template: String,
) {
    CHARACTER("/character"),
    CHARACTER_BY_ID("/character/{id}"),
    EPISODES_BY_IDS("/episode/{ids}"),
}

/** A filter's name from the allow-list; its value is never logged. */
public enum class FilterName(
    public val wireName: String,
) {
    NAME("name"),
    STATUS("status"),
}

/** The status family of a response, never its code or message; `NO_RESPONSE` when none arrived. */
public enum class StatusFamily(
    public val wireName: String,
) {
    SUCCESSFUL("2XX"),
    CLIENT_ERROR("4XX"),
    SERVER_ERROR("5XX"),
    NO_RESPONSE("NO_RESPONSE"),
}

public enum class LogOutcome { SUCCESS, EMPTY, FAILURE, CANCELLED }

/** The failure *type* — the REST families of `ApiFailure` — never its message. */
public enum class ErrorClass {
    OFFLINE,
    TIMEOUT,
    NOT_FOUND,
    INVALID_REQUEST,
    RATE_LIMITED,
    SERVER,
    MALFORMED_RESPONSE,
    EMPTY_BODY,
    UNKNOWN,
}

public enum class LogScreen { SPLASH, DISCOVERY, CHARACTER_DETAIL, FAVORITES, EPISODES, SETTINGS }

/** The component a store or cache event concerns; never a store path or key. */
public enum class LogComponent { RESPONSE_CACHE, IMAGE_CACHE, FAVORITES_STORE, PAGER }

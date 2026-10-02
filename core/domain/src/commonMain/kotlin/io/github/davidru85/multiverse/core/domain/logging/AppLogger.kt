package io.github.davidru85.multiverse.core.domain.logging

/**
 * The one logging contract (`IC-024`, `DEC-087`, ADR-0013): every layer that may log reaches this
 * interface, and the shells supply only the platform sink. Levels mean exactly what
 * `OBSERVABILITY.md` §2.1 says; a release build enables `ERROR` only, with no override (`DEC-039`).
 */
public enum class LogLevel { DEBUG, INFO, WARN, ERROR }

public interface AppLogger {
    /** Whether an event at [level] can reach a sink in this build. */
    public fun isEnabled(level: LogLevel): Boolean

    /** Records [event] at its catalogue level. Never throws, never blocks. */
    public fun log(event: LogEvent)
}

/** Builds the event only when its level is enabled (`OBSERVABILITY.md` §7 rule 3). */
public inline fun AppLogger.log(
    level: LogLevel,
    event: () -> LogEvent,
) {
    if (isEnabled(level)) log(event())
}

/**
 * Where validated records go (`DEC-093`): a platform sink in an app shell, the debug-only diagnostic
 * recorder of `:core:diagnostics`, or a test's recording sink. A sink may fail; the logger then loses
 * the record rather than queueing it (`OBSERVABILITY.md` §7).
 */
public fun interface LogSink {
    public fun write(record: LogRecord)
}

/** One validated record: its catalogue id and level, and only the permitted fields that passed validation. */
public data class LogRecord(
    public val level: LogLevel,
    public val catalogueId: String,
    public val fields: Map<LogField, String>,
)

/** The permitted field list of `OBSERVABILITY.md` §2.2, by its wire name. A field outside it does not exist. */
public enum class LogField(
    public val wireName: String,
) {
    PROTOCOL("protocol"),
    OPERATION("operation"),
    PATH_TEMPLATE("pathTemplate"),
    PAGE("page"),
    FILTER_NAMES("filterNames"),
    STATUS_FAMILY("statusFamily"),
    CACHE_SOURCE("cacheSource"),
    IS_STALE("isStale"),
    DURATION_MS("durationMs"),
    CORRELATION_ID("correlationId"),
    OUTCOME("outcome"),
    ERROR_CLASS("errorClass"),
    SCREEN("screen"),
    COMPONENT("component"),
    RETRY_AFTER_SECONDS("retryAfterSeconds"),
    APP_VERSION("appVersion"),
    PLATFORM("platform"),
    BUILD_TYPE("buildType"),
    CAUSE("cause"),
}

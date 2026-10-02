package io.github.davidru85.multiverse.core.domain.result

/**
 * The outcome of a data seam (`API_SPECS.md` §3, `IC-003`): an expected remote failure is a value,
 * not control flow, so it stays visible to the compiler and never crosses the Kotlin→Swift boundary
 * as an exception (`DEC-013`). A `CancellationException` is never converted into a [Failure].
 */
public sealed interface DataResult<out T> {
    /** The source that produced this outcome; decided by the data layer only. */
    public val source: DataSource

    /** Non-empty only for a usable but incomplete response; never rendered as a failure. */
    public val warnings: List<ApiWarning>

    /**
     * A fully decoded, domain-valid value. [isStale] means "served from a cache past freshness", so
     * a stale success never has a [DataSource.NETWORK] source.
     */
    public data class Success<T>(
        public val value: T,
        override val source: DataSource,
        public val isStale: Boolean,
        override val warnings: List<ApiWarning> = emptyList(),
    ) : DataResult<T> {
        init {
            require(!(isStale && source == DataSource.NETWORK)) {
                "A stale success comes from a cache; a network value is never stale (IC-003)"
            }
        }
    }

    /** A failure: no value, and a [source] that is never a cache hit (`API_SPECS.md` §7.3). */
    public data class Failure(
        public val failure: ApiFailure,
        override val source: DataSource,
        override val warnings: List<ApiWarning> = emptyList(),
    ) : DataResult<Nothing>
}

/** Where a value or a failure came from. */
public enum class DataSource { NETWORK, MEMORY_CACHE, DISK_CACHE }

/** A note on a usable but incomplete response, such as a batch that omitted a requested id. */
public data class ApiWarning(
    public val code: String,
    public val detail: String? = null,
)

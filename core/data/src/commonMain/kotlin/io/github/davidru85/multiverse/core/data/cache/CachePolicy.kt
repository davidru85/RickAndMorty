package io.github.davidru85.multiverse.core.data.cache

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The app's freshness bands (`DEC-012`, `API_SPECS.md` §7.1) as injectable configuration, never
 * constants in the transport layer. A test substitutes the windows and moves an injected clock, so
 * no case waits or depends on wall-clock time (`REQ-REL-004`).
 *
 * | Age of the entry | Online | Offline |
 * | --- | --- | --- |
 * | `≤ fresh` (24 h) | Serve from cache; no network request. A manual refresh still performs one | Serve from cache, `isStale = false` |
 * | `> fresh` and `≤ staleWhileRevalidate` (7 d) | Serve the cached content immediately and revalidate in the background | Serve from cache with `isStale = true` |
 * | `> staleWhileRevalidate` and `≤ offlineFallback` (30 d) | Network first; the entry is the fallback if the request fails | Serve from cache with `isStale = true` |
 * | `> offlineFallback`, or no entry | Network; on failure surface the failure | No entry: surface the failure, never an empty page |
 */
public data class CachePolicy(
    public val fresh: Duration = 24.hours,
    public val staleWhileRevalidate: Duration = 7.days,
    public val offlineFallback: Duration = 30.days,
) {
    init {
        require(fresh > Duration.ZERO) { "the fresh window is positive" }
        require(staleWhileRevalidate >= fresh) { "stale-while-revalidate covers the fresh window" }
        require(offlineFallback >= staleWhileRevalidate) { "the offline fallback covers the revalidate window" }
    }
}

/** Where a stored entry stands against [CachePolicy] at the moment of a read. */
public enum class CacheFreshness {
    /** Inside the fresh window: the entry answers the request and no network request is made. */
    FRESH,

    /** Past freshness, inside the revalidate window: usable now, and worth revalidating. */
    STALE,

    /** Past the revalidate window, inside the offline fallback: usable only if the network fails. */
    OFFLINE_FALLBACK,

    /** Past the offline fallback: not usable at all. */
    EXPIRED,
}

package io.github.davidru85.multiverse.core.designsystem.image

import androidx.compose.ui.graphics.Color
import io.github.davidru85.multiverse.core.designsystem.color.AccentPipeline
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseBrandColors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A portrait's pixels, as the accent policy receives them (`DEC-097`, ADR-0015): the seam's job is
 * pixels off the image; the policy's job is the colour. Keeping them apart is what lets the policy be
 * tested with synthetic pixels and no network, no filesystem and no decoder.
 */
public fun interface PortraitPixels {
    /** The portrait's pixels as packed ARGB, or `null` when the image could not be read. */
    public suspend fun pixelsFor(imageUrl: String): IntArray?
}

/**
 * The accent policy (`UI_SPEC.md` §5.4, `DEC-097`): the container colour a card paints itself with,
 * derived from its portrait.
 *
 * Three rules the specification fixes, each observable:
 *
 * - the computation runs **off the main thread**, on the injected [dispatcher] — never on the caller's;
 * - the result is memoised **once per URL** in a bounded LRU, so extraction happens at most once per
 *   character per process and the cache evicts in a documented order;
 * - a portrait that cannot be read, or one with nothing scoreable, falls back to **Portal Green**,
 *   which is a token rather than an invented colour.
 *
 * The colour maths lives in [AccentPipeline], which is platform-free, so this class is only the policy:
 * where the work runs and what is remembered.
 */
public class CharacterAccentPolicy(
    private val pixels: PortraitPixels,
    private val dispatcher: CoroutineDispatcher,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val cache = LinkedHashMap<String, Color>()
    private val lock = Mutex()
    private var extractions = 0

    /**
     * The container colour for [imageUrl]. The first call computes and remembers it; every later call
     * for the same URL answers from the memo without touching the pixels again.
     */
    public suspend fun accentFor(imageUrl: String): Color {
        lock.withLock {
            cache.remove(imageUrl)?.let { memoised ->
                // A hit is re-inserted so the order tracks use, which is what makes the eviction
                // "least recently used" rather than "least recently inserted".
                cache[imageUrl] = memoised
                return memoised
            }
        }

        val computed =
            withContext(dispatcher) {
                val sampled = pixels.pixelsFor(imageUrl)
                val container = sampled?.let(AccentPipeline::containerFor)
                container?.let { Color(it) } ?: MultiverseBrandColors.portalGreen
            }

        // The counter is part of the memo's state, so it is updated under the same lock that inserts
        // the entry and cannot race with a concurrent caller.
        lock.withLock { extractions += 1 }

        lock.withLock {
            // A bounded LRU: the least recently used entry leaves first, and a hit re-inserts so the
            // order tracks use rather than arrival.
            cache.remove(imageUrl)
            cache[imageUrl] = computed
            while (cache.size > capacity) {
                val eldest = cache.keys.first()
                cache.remove(eldest)
            }
        }
        return computed
    }

    /** The URLs the memo currently holds, eldest first, so a test can assert the eviction order. */
    public suspend fun memoised(): List<String> = lock.withLock { cache.keys.toList() }

    /** How many extractions ran, so a test can assert the memo rather than guess at it. */
    public suspend fun extractionCount(): Int = lock.withLock { extractions }

    public companion object {
        /** The memo size `UI_SPEC.md` §5.4 implies for a scrolling grid of characters. */
        public const val DEFAULT_CAPACITY: Int = 64
    }
}

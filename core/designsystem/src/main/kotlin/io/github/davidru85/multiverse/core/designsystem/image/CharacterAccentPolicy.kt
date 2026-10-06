package io.github.davidru85.multiverse.core.designsystem.image

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.davidru85.multiverse.core.designsystem.color.AccentPipeline
import io.github.davidru85.multiverse.core.designsystem.color.TonalPalette
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseBrandColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
 * - the computation runs **off the main thread**, on the injected [dispatcher] — never on the caller's —
 *   and **one extraction at a time** (`TASK-128`): a fling brings several cards on screen together, and
 *   parallel extractions took the cores the UI thread and the render thread needed;
 * - the result is memoised **once per URL** in a bounded LRU, so extraction happens at most once per
 *   character per process and the cache evicts in a documented order; callers asking for a URL whose
 *   extraction is already running wait for that one rather than starting another;
 * - a portrait that cannot be read, or one with nothing scoreable, falls back to **Portal Green** as
 *   the source colour — its tone-30 container, like every other card's — which is a token rather than
 *   an invented colour, and never the bright token itself under On Surface text.
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
    private val inFlight = mutableMapOf<String, CompletableDeferred<Color>>()
    private val lock = Mutex()

    /** One permit: at most one extraction runs at a time, whatever the dispatcher's parallelism. */
    private val extraction = Semaphore(1)

    /**
     * The container colour for [imageUrl]. The first call computes and remembers it; every later call
     * for the same URL answers from the memo without touching the pixels again.
     */
    public suspend fun accentFor(imageUrl: String): Color {
        // Under the lock: a memo hit, or the extraction already running for this URL, or a new one this
        // caller owns. Deciding all three together is what keeps two first callers from both extracting.
        val (pending, owner) =
            lock.withLock {
                cache.remove(imageUrl)?.let { memoised ->
                    // A hit is re-inserted so the order tracks use, which is what makes the eviction
                    // "least recently used" rather than "least recently inserted".
                    cache[imageUrl] = memoised
                    return memoised
                }
                inFlight[imageUrl]?.let { running -> running to false }
                    ?: CompletableDeferred<Color>().also { inFlight[imageUrl] = it }.let { it to true }
            }
        if (!owner) return pending.await()

        val computed =
            try {
                extraction.withPermit {
                    withContext(dispatcher) {
                        val sampled = pixels.pixelsFor(imageUrl)
                        val container = sampled?.let(AccentPipeline::containerFor)
                        Color(container ?: TonalPalette.container(MultiverseBrandColors.portalGreen.toArgb()))
                    }
                }
            } catch (failure: Throwable) {
                // Cancellation included: the waiting callers see the same outcome, and the next caller
                // starts afresh rather than awaiting an extraction that will never finish.
                lock.withLock { inFlight.remove(imageUrl) }
                pending.completeExceptionally(failure)
                throw failure
            }

        lock.withLock {
            // A bounded LRU: the least recently used entry leaves first, and a hit re-inserts so the
            // order tracks use rather than arrival.
            inFlight.remove(imageUrl)
            cache.remove(imageUrl)
            cache[imageUrl] = computed
            while (cache.size > capacity) {
                val eldest = cache.keys.first()
                cache.remove(eldest)
            }
        }
        pending.complete(computed)
        return computed
    }

    public companion object {
        /** The memo size `UI_SPEC.md` §5.4 implies for a scrolling grid of characters. */
        public const val DEFAULT_CAPACITY: Int = 64
    }
}

/**
 * The accent policy the shell provides for every card (`UI_SPEC.md` §5.4, `DEC-097`); `null` outside the
 * shell, where a card keeps Surface Container High.
 */
public val LocalCharacterAccentPolicy: ProvidableCompositionLocal<CharacterAccentPolicy?> = staticCompositionLocalOf { null }

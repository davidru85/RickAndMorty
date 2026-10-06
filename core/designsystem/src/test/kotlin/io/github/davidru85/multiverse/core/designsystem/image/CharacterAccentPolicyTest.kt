package io.github.davidru85.multiverse.core.designsystem.image

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.davidru85.multiverse.core.designsystem.color.TonalPalette
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseBrandColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TEST-UNIT-035`, the accent-policy cases (`REQ-FUNC-005`, `UI_SPEC.md` §5.4, `DEC-097`).
 *
 * The policy has four observable rules, and each has a case: the container is **tone 30**, the chroma
 * is **clamped to `[24, 48]`**, an unreadable portrait falls back to **Portal Green**, and extraction
 * happens **once per URL** with an LRU that evicts eldest-first. The pixels come from a fake source, so
 * no image, decoder or network is involved.
 */
class CharacterAccentPolicyTest {
    private val redPixels = IntArray(64) { 0xFFE53935.toInt() }
    private val greyPixels = IntArray(64) { 0xFF808080.toInt() }
    private val oversaturated = IntArray(64) { 0xFFFF0000.toInt() }

    private fun TestScope.policy(
        source: PortraitPixels,
        capacity: Int = 64,
    ) = CharacterAccentPolicy(
        pixels = source,
        // The case's own scheduler, so the policy's work runs in the same virtual time as the test's.
        dispatcher = StandardTestDispatcher(testScheduler, name = "accent"),
        capacity = capacity,
    )

    private fun pixelsOf(vararg pairs: Pair<String, IntArray>) = PortraitPixels { url -> pairs.firstOrNull { it.first == url }?.second }

    @Test
    fun `TEST-UNIT-035 given_a_red_portrait_when_the_accent_resolves_then_the_container_is_tone_thirty_with_the_clamped_chroma`() =
        runTest {
            val accent = policy(pixelsOf("red" to redPixels)).accentFor("red")
            val argb = accent.toArgbValue()

            // Tone 30 is the CIE L* lightness `UI_SPEC.md` §5.4 means by it — established from the six
            // reference accents the specification records, which all measure L* = 30.0 ± 0.2 — so the case
            // asserts the quantity a reviewer measures. Asserting the mean sRGB channel instead (as this
            // case did before `TASK-045`) could not see the construction's defect: it asserted the very
            // quantity the old construction fixed, while the colour sat at L* 57 and on-surface text over
            // it fell to 2.8:1, below the 4.5:1 floor of `REQ-UX-003`.
            assertEquals(
                "the container sits at tone 30 in CIE L* (UI_SPEC.md §5.4)",
                TonalPalette.CONTAINER_TONE,
                TonalPalette.luminanceOf(argb),
                0.6,
            )
            assertNotEquals(
                "an accent must not be the portrait's own colour",
                redPixels[0],
                argb,
            )
        }

    @Test
    fun `TEST-UNIT-035 given_pixels_outside_the_documented_chroma_range_when_the_accent_resolves_then_the_chroma_is_clamped`() =
        runTest {
            val faint = IntArray(64) { 0xFFF0E8E8.toInt() }
            val policy = policy(pixelsOf("over" to oversaturated, "grey" to greyPixels, "faint" to faint))
            val over = policy.accentFor("over").toArgbValue()
            val grey = policy.accentFor("grey").toArgbValue()
            val faintAccent = policy.accentFor("faint").toArgbValue()

            // The clamp is observable as saturation: a fully saturated portrait cannot produce a canvas
            // more saturated than the ceiling, and a grey one cannot be less than the floor.
            assertTrue(
                "a saturated portrait's container stays within the ceiling: ${saturationOf(over)}",
                saturationOf(over) <= TonalPalette.MAX_CHROMA + 0.5,
            )
            assertTrue(
                "a faint tint is raised to the floor: ${saturationOf(faintAccent)}",
                saturationOf(faintAccent) >= TonalPalette.MIN_CHROMA - 0.5,
            )
            assertEquals(
                "a grey portrait has no hue to tint, so its container stays neutral",
                0.0,
                saturationOf(grey),
                1.0,
            )
        }

    @Test
    fun `TEST-UNIT-035 given_a_portrait_that_cannot_be_read_when_the_accent_resolves_then_it_falls_back_to_portal_green`() =
        runTest {
            val accent = policy(pixelsOf()).accentFor("missing")
            // `UI_SPEC.md` §5.4 step 1 falls back to Portal Green as the **source** colour, so the
            // container is its tone 30 like every other card's — never the raw, bright token under
            // On Surface text (`TASK-113`).
            assertEquals(
                "an unreadable portrait falls back to Portal Green's tone-30 container, never an invented colour",
                Color(TonalPalette.container(MultiverseBrandColors.portalGreen.toArgb())),
                accent,
            )
        }

    @Test
    fun `TEST-UNIT-035 given_a_portrait_of_one_flat_colour_when_the_accent_resolves_then_it_still_produces_a_container`() =
        runTest {
            // A flat colour is the degenerate input the quantizer must still answer for; the fallback is
            // only for an image that cannot be read at all.
            val accent = policy(pixelsOf("flat" to greyPixels)).accentFor("flat")
            assertNotEquals(MultiverseBrandColors.portalGreen, accent)
        }

    @Test
    fun `TEST-UNIT-035 given_the_same_url_twice_when_the_accent_resolves_then_the_pixels_are_read_once`() =
        runTest {
            var reads = 0
            val source =
                PortraitPixels {
                    reads += 1
                    redPixels
                }
            val policy = policy(source)

            val first = policy.accentFor("url")
            val second = policy.accentFor("url")

            assertEquals("the memo answers the second call", first, second)
            assertEquals("extraction runs at most once per URL per process", 1, reads)
        }

    /**
     * `TEST-UNIT-107` (`TASK-128`, `GAP-038`): extractions run **one at a time**. A fling brings several
     * cards on screen at once; when each started its own extraction on the shared dispatcher, the colour
     * work took every core the UI thread and the render thread needed. The fake source suspends inside
     * each read, so overlapping extractions are observable as reads in flight together.
     */
    @Test
    fun `TEST-UNIT-107 given_several_portraits_at_once_when_their_accents_resolve_then_one_extraction_runs_at_a_time`() =
        runTest {
            var inFlight = 0
            var peak = 0
            val source =
                PortraitPixels {
                    inFlight += 1
                    peak = maxOf(peak, inFlight)
                    delay(10)
                    inFlight -= 1
                    redPixels
                }
            val policy = policy(source)

            val accents = (1..4).map { index -> async { policy.accentFor("portrait-$index") } }.awaitAll()

            assertEquals("every portrait still resolves its accent", 4, accents.size)
            assertEquals("TEST-UNIT-107: at most one extraction runs at a time", 1, peak)
        }

    /** A pixel source that counts its reads per URL, so the memo is observed through what it saves. */
    private class CountingPixels(
        private val pixels: IntArray,
    ) : PortraitPixels {
        val reads = mutableMapOf<String, Int>()

        override suspend fun pixelsFor(imageUrl: String): IntArray {
            reads[imageUrl] = (reads[imageUrl] ?: 0) + 1
            return pixels
        }
    }

    @Test
    fun `TEST-UNIT-035 given_a_small_memo_when_urls_exceed_it_then_the_eldest_is_evicted_first`() =
        runTest {
            val source = CountingPixels(redPixels)
            val policy = policy(source, capacity = 2)
            policy.accentFor("a")
            policy.accentFor("b")
            policy.accentFor("c") // evicts the eldest, `a`

            policy.accentFor("b")
            policy.accentFor("c")
            assertEquals("the two newest entries are still remembered", mapOf("a" to 1, "b" to 1, "c" to 1), source.reads)
            policy.accentFor("a")
            assertEquals("the least recently used entry left first", 2, source.reads["a"])
        }

    @Test
    fun `TEST-UNIT-035 given_a_memo_hit_when_it_is_read_again_then_the_entry_is_refreshed_not_evicted`() =
        runTest {
            val source = CountingPixels(redPixels)
            val policy = policy(source, capacity = 2)
            policy.accentFor("a")
            policy.accentFor("b")
            policy.accentFor("a") // a hit, so `a` becomes the most recent and `b` the eldest
            policy.accentFor("c") // evicts the eldest, which is now `b`

            policy.accentFor("a")
            assertEquals("a hit protects its entry from the next eviction", 1, source.reads["a"])
            policy.accentFor("b")
            assertEquals("the entry that was not hit left instead", 2, source.reads["b"])
        }

    /**
     * `TEST-UNIT-099` — one extraction per URL in flight (`TASK-115`): two cards of the same character
     * asking at once each ran an extraction, because the memo is only written when one finishes.
     */
    @Test
    fun `TEST-UNIT-099 given_two_first_calls_for_one_url_at_once_when_both_resolve_then_the_pixels_are_read_once`() =
        runTest {
            val source = CountingPixels(redPixels)
            val policy = policy(source)

            val first = async { policy.accentFor("url") }
            val second = async { policy.accentFor("url") }

            assertEquals("both callers get the same accent", first.await(), second.await())
            assertEquals("a URL in flight is extracted once", 1, source.reads["url"])
        }

    @Test
    fun `TEST-UNIT-035 given_an_injected_dispatcher_when_the_accent_resolves_then_the_work_is_dispatched_to_it`() =
        runTest {
            // Virtual time runs every dispatcher on one thread, so a thread-name assertion would pass
            // vacuously. What the rule means is that the work is **dispatched** rather than run inline on
            // the caller's stack, which a counting dispatcher observes exactly.
            val dispatches =
                java.util.concurrent.atomic
                    .AtomicInteger()
            val recording =
                object : kotlinx.coroutines.CoroutineDispatcher() {
                    private val delegate = StandardTestDispatcher(testScheduler, name = "accent")

                    override fun dispatch(
                        context: kotlin.coroutines.CoroutineContext,
                        block: Runnable,
                    ) {
                        dispatches.incrementAndGet()
                        delegate.dispatch(context, block)
                    }
                }
            var readOnCallerStack = false
            val source =
                PortraitPixels {
                    readOnCallerStack = dispatches.get() == 0
                    redPixels
                }
            val policy = CharacterAccentPolicy(pixels = source, dispatcher = recording)

            policy.accentFor("url")

            assertTrue("the accent work is dispatched to the injected dispatcher", dispatches.get() > 0)
            assertTrue("the pixels are not read inline on the caller's stack", !readOnCallerStack)
        }

    private fun androidx.compose.ui.graphics.Color.toArgbValue(): Int =
        (0xFF shl 24) or
            ((red * 255f).toInt() shl 16) or
            ((green * 255f).toInt() shl 8) or
            (blue * 255f).toInt()

    /** The chroma on the policy's documented scale, so a case reads the clamp directly. */
    private fun saturationOf(argb: Int): Double {
        val red = (argb shr 16 and 0xFF).toDouble()
        val green = (argb shr 8 and 0xFF).toDouble()
        val blue = (argb and 0xFF).toDouble()
        return (maxOf(red, green, blue) - minOf(red, green, blue)) / 255.0 * 100.0
    }
}

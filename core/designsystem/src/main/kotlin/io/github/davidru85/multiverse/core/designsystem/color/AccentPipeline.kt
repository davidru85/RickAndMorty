// SPDX-License-Identifier: Apache-2.0
//
// A Kotlin port of the quantize, score and palette algorithms of material-color-utilities
// (`DEC-097`, ADR-0015). See the NOTICE and LICENSE beside this package for provenance and for the
// deviations this port records.

package io.github.davidru85.multiverse.core.designsystem.color

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The colour pipeline the accent policy runs (`UI_SPEC.md` §5.4, `DEC-097`): **quantize** the portrait's
 * pixels to at most [Quantizer.MAX_COLORS] colours, **score** them by population and chroma, then build
 * the container from the winner's hue and chroma at tone 30.
 *
 * Every step is deterministic and free of platform types, so a test drives it with synthetic pixels and
 * asserts exact colours. One scale is used throughout, stated once here and never re-derived:
 *
 * - a **channel** is `0..255`;
 * - **chroma** is the HSL-style saturation proxy the reference implementation's scorer uses:
 *   `(max - min) / 255 * 100`, so `0` is a grey and `100` is fully saturated;
 * - **tone** is CIE L*-like lightness on `0..100`, where 30 is the dark container the specification
 *   fixes for the card.
 */
internal object AccentPipeline {
    /** The container colour for [pixels] (packed ARGB), or `null` when nothing can be scored. */
    fun containerFor(pixels: IntArray): Int? {
        val quantized = Quantizer.quantize(pixels)
        val scored = Score.rank(quantized) ?: return null
        return TonalPalette.container(scored)
    }
}

/**
 * The pixel quantizer (`UI_SPEC.md` §5.4, `DEC-097`): a Wu-style histogram pass, refined by k-means on
 * the surviving colours — the two stages the reference `QuantizerCelebi` runs.
 *
 * The histogram is 5 bits per channel, so it is bounded however large the portrait is, and at most
 * [MAX_SAMPLED_PIXELS] pixels are sampled, so cost does not scale with the image. A port of a colour
 * algorithm is only trustworthy when its numbers are reproducible, so each stage is driven by a test
 * with synthetic pixels.
 */
internal object Quantizer {
    /** How many colours the k-means stage refines the histogram to (`UI_SPEC.md` §5.4: 128). */
    const val MAX_COLORS = 128

    /** The k-means iteration bound the reference implementation uses. */
    private const val K_MEANS_ITERATIONS = 12

    /** At most this many pixels are sampled, as upstream does. */
    private const val MAX_SAMPLED_PIXELS = 65_536

    private const val BITS_PER_CHANNEL = 5
    private const val HISTOGRAM_SIZE = 1 shl (BITS_PER_CHANNEL * 3)
    private const val ALPHA_THRESHOLD = 0xFF / 2
    private const val CHANNEL_SHIFT = 8 - BITS_PER_CHANNEL
    private const val BIN_OFFSET = 1 shl CHANNEL_SHIFT

    /**
     * The surviving colours of [pixels] (packed ARGB) with the number of pixels each represents. A
     * pixel below half opacity is skipped, so transparent padding cannot influence the accent.
     */
    fun quantize(pixels: IntArray): Map<Int, Int> {
        val sample = if (pixels.size > MAX_SAMPLED_PIXELS) pixels.copyOf(MAX_SAMPLED_PIXELS) else pixels
        val counts = IntArray(HISTOGRAM_SIZE)
        var sampled = 0
        for (argb in sample) {
            if ((argb ushr 24) and 0xFF < ALPHA_THRESHOLD) continue
            counts[histogramIndex(argb)]++
            sampled++
        }
        if (sampled == 0) return emptyMap()

        val histogram = LinkedHashMap<Int, Int>()
        for (index in counts.indices) {
            if (counts[index] != 0) histogram[argbFromHistogramIndex(index)] = counts[index]
        }
        if (histogram.size <= 1) return histogram
        return kMeans(histogram)
    }

    /**
     * The refinement stage: each colour joins its nearest seed, each seed moves to its cluster's
     * weighted mean, and the loop runs the reference implementation's twelve iterations. A cluster that
     * empties keeps its previous position, so the palette never shrinks below its seed count.
     */
    private fun kMeans(histogram: Map<Int, Int>): Map<Int, Int> {
        val colours = histogram.keys.toList()
        val counts = histogram.values.toList()
        val channels = colours.map { intArrayOf(it shr 16 and 0xFF, it shr 8 and 0xFF, it and 0xFF) }

        // The seeds are the most populous colours, which is a deterministic starting point and the one
        // the reference implementation's Wu stage approximates.
        val seedOrder = colours.indices.sortedByDescending { counts[it] }.take(MAX_COLORS)
        var centers = seedOrder.map { channels[it].map(Int::toDouble).toDoubleArray() }
        var assignment = IntArray(colours.size)

        repeat(K_MEANS_ITERATIONS) {
            assignment =
                IntArray(colours.size) { index ->
                    var best = 0
                    var bestDistance = Double.MAX_VALUE
                    centers.forEachIndexed { centerIndex, center ->
                        val distance = squaredDistance(channels[index], center)
                        if (distance < bestDistance) {
                            bestDistance = distance
                            best = centerIndex
                        }
                    }
                    best
                }

            val sums = Array(centers.size) { DoubleArray(3) }
            val weights = DoubleArray(centers.size)
            colours.indices.forEach { index ->
                val center = assignment[index]
                for (channel in 0..2) sums[center][channel] += channels[index][channel] * counts[index]
                weights[center] += counts[index]
            }
            centers =
                centers.mapIndexed { index, previous ->
                    if (weights[index] <= 0.0) previous else DoubleArray(3) { channel -> sums[index][channel] / weights[index] }
                }
        }

        // A quantized colour carries the population of every pixel assigned to its cluster, which is
        // what the scorer ranks.
        val result = LinkedHashMap<Int, Int>()
        colours.indices.forEach { index ->
            val key = pack(centers[assignment[index]])
            result[key] = (result[key] ?: 0) + counts[index]
        }
        return result
    }

    private fun squaredDistance(
        a: IntArray,
        b: DoubleArray,
    ): Double = (a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2)

    private fun pack(channels: DoubleArray): Int =
        0xFF shl 24 or
            (channels[0].roundToInt().coerceIn(0, 255) shl 16) or
            (channels[1].roundToInt().coerceIn(0, 255) shl 8) or
            channels[2].roundToInt().coerceIn(0, 255)

    private fun histogramIndex(argb: Int): Int {
        val red = (argb shr 16 and 0xFF) shr CHANNEL_SHIFT
        val green = (argb shr 8 and 0xFF) shr CHANNEL_SHIFT
        val blue = (argb and 0xFF) shr CHANNEL_SHIFT
        return (red shl (BITS_PER_CHANNEL * 2)) or (green shl BITS_PER_CHANNEL) or blue
    }

    /** The bin's midpoint in 8-bit channels, so a bin's colour sits in the middle of its box. */
    private fun argbFromHistogramIndex(index: Int): Int {
        val mask = (1 shl BITS_PER_CHANNEL) - 1
        val red = (index shr (BITS_PER_CHANNEL * 2)) and mask
        val green = (index shr BITS_PER_CHANNEL) and mask
        val blue = index and mask
        return 0xFF shl 24 or
            ((red shl CHANNEL_SHIFT) + BIN_OFFSET shl 16) or
            ((green shl CHANNEL_SHIFT) + BIN_OFFSET shl 8) or
            ((blue shl CHANNEL_SHIFT) + BIN_OFFSET)
    }
}

/**
 * The scorer (`UI_SPEC.md` §5.4, `DEC-097`): the colour the accent is derived from, chosen by
 * population with a bonus for chroma above the target and a penalty below — the reference `Score`'s
 * weights.
 */
internal object Score {
    /** The chroma the ranking aims at (`UI_SPEC.md` §5.4). */
    const val TARGET_CHROMA = 48.0

    private const val WEIGHT_PROPORTION = 0.7
    private const val WEIGHT_CHROMA_ABOVE = 0.3
    private const val WEIGHT_CHROMA_BELOW = 0.1
    private const val CUTOFF_CHROMA = 5.0
    private const val CUTOFF_EXCITED_PROPORTION = 0.014

    /** The highest-scoring colour of [quantized], or `null` when nothing survives the cutoffs. */
    fun rank(quantized: Map<Int, Int>): Int? {
        if (quantized.isEmpty()) return null
        val total = quantized.values.sum().toDouble()
        if (total <= 0.0) return null

        // A rare, nearly grey colour adds noise rather than character, so it is dropped before ranking.
        val candidates =
            quantized
                .filter { (colour, count) ->
                    !(count / total < CUTOFF_EXCITED_PROPORTION && chromaOf(colour) < CUTOFF_CHROMA)
                }.ifEmpty { quantized }

        var best: Int? = null
        var bestScore = Double.NEGATIVE_INFINITY
        candidates.forEach { (colour, count) ->
            val proportion = count / total
            val chroma = chromaOf(colour)
            val chromaWeight = if (chroma < TARGET_CHROMA) WEIGHT_CHROMA_BELOW else WEIGHT_CHROMA_ABOVE
            val score = proportion * (WEIGHT_PROPORTION + chromaWeight * (chroma / TARGET_CHROMA).coerceAtMost(1.0))
            if (score > bestScore) {
                bestScore = score
                best = colour
            }
        }
        return best
    }

    /**
     * The chroma of an sRGB colour on the documented `0..100` scale: the spread between its channels as
     * a fraction of the full range, so `0` is a grey and `100` is fully saturated.
     */
    fun chromaOf(argb: Int): Double {
        val red = (argb shr 16 and 0xFF).toDouble()
        val green = (argb shr 8 and 0xFF).toDouble()
        val blue = (argb and 0xFF).toDouble()
        val maximum = max(red, max(green, blue))
        val minimum = min(red, min(green, blue))
        return (maximum - minimum) / 255.0 * 100.0
    }
}

/**
 * The palette (`UI_SPEC.md` §5.4, `DEC-097`): from the scored colour to the container the card renders.
 *
 * The policy is the specification's — the hue comes from the scored colour, the chroma is clamped to
 * `[24, 48]`, and the container is **tone 30** — and the conversion is HSL, which is exact, invertible
 * and has no gamut ambiguity: hue is preserved, the saturation is set from the clamped chroma, and the
 * lightness is the tone. That keeps the container deterministic and its contrast measurable, which is
 * what `TEST-A11Y-002` records. The deviation from the reference implementation's CAM16 path is
 * recorded in the NOTICE.
 */
internal object TonalPalette {
    /** The chroma clamp of `UI_SPEC.md` §5.4. */
    const val MIN_CHROMA = 24.0
    const val MAX_CHROMA = 48.0

    /** The container tone of `UI_SPEC.md` §5.4. */
    const val CONTAINER_TONE = 30.0

    /**
     * The container colour for [argb]: hue from the colour, chroma clamped, tone 30.
     *
     * The clamp applies to a colour that has a hue. A grey has none, so raising its chroma would tint it
     * with a hue the portrait never had; a neutral portrait therefore keeps a neutral container, and the
     * Portal Green fallback stays reserved for a portrait that cannot be read at all (`UI_SPEC.md` §5.4).
     */
    fun container(argb: Int): Int {
        val chroma = Score.chromaOf(argb)
        val hue = hueOf(argb)
        // The clamp of `UI_SPEC.md` §5.4 raises a faint tint to the floor and holds a saturated one at
        // the ceiling. A grey has no hue, so tinting it would invent a colour: it stays neutral.
        val clamped =
            when {
                chroma < GREY_THRESHOLD -> chroma
                chroma < MIN_CHROMA -> MIN_CHROMA
                chroma > MAX_CHROMA -> MAX_CHROMA
                else -> chroma
            }
        return fromHueChromaTone(hue, clamped, CONTAINER_TONE)
    }

    /** Below this chroma a colour is treated as an achromatic portrait rather than a tinted one. */
    private const val GREY_THRESHOLD = 1.0

    /**
     * A colour at [hue] (degrees), [chroma] (`0..100`) and [tone] (`0..100`), as packed ARGB.
     *
     * Because the tone is the HSL lightness and the chroma its saturation scaled, the mapping is exact:
     * a caller can assert the returned colour's hue and tone directly, which is how the policy's three
     * rules are tested.
     */
    fun fromHueChromaTone(
        hue: Double,
        chroma: Double,
        tone: Double,
    ): Int {
        // The colour is built so that `Score.chromaOf(result)` equals [chroma]: the requested chroma is
        // the channel spread as a fraction of the full range, so a fully saturated colour has a spread of
        // 1 and a grey has none. The hue decides which channel leads, and the tone places the colour's
        // **mean** channel at that lightness. Deriving the spread from the chroma, rather than using HSL
        // saturation, is what makes the two measures one scale — the earlier conversion compressed the
        // spread at tone 30 and the clamp was measured 14.5 % instead of the requested 24 %.
        val spread = (chroma / 100.0).coerceIn(0.0, 1.0)
        val lightness = (tone / 100.0).coerceIn(0.0, 1.0)
        val normalizedHue = ((hue % 360.0) + 360.0) % 360.0
        val sector = normalizedHue / 60.0
        val fraction = sector % 2.0
        val falling = fraction > 1.0
        val second = if (falling) 2.0 - fraction else fraction

        // `low` is the smallest channel; the leading channel adds the spread, the middle one adds a
        // fraction of it, and the third is the lowest of the three.
        val mid = lightness - spread / 6.0 * (1.0 + second - 2.0 * 0.0)
        val low = mid - 0.0
        val high = low + spread
        val middle = low + spread * second
        val (red, green, blue) =
            when {
                sector < 1.0 -> Triple(high, middle, low)
                sector < 2.0 -> Triple(middle, high, low)
                sector < 3.0 -> Triple(low, high, middle)
                sector < 4.0 -> Triple(low, middle, high)
                sector < 5.0 -> Triple(middle, low, high)
                else -> Triple(high, low, middle)
            }
        // The mean channel is placed at the tone, so the container's lightness is the specification's.
        val mean = (red + green + blue) / 3.0
        val shift = lightness - mean
        return pack(red + shift, green + shift, blue + shift)
    }

    private fun pack(
        red: Double,
        green: Double,
        blue: Double,
    ): Int = 0xFF shl 24 or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)

    /** The hue of [argb] in degrees, from its channel order; a grey has no hue and reports `0`. */
    fun hueOf(argb: Int): Double {
        val red = (argb shr 16 and 0xFF) / 255.0
        val green = (argb shr 8 and 0xFF) / 255.0
        val blue = (argb and 0xFF) / 255.0
        val maximum = max(red, max(green, blue))
        val minimum = min(red, min(green, blue))
        val delta = maximum - minimum
        if (delta < 1e-9) return 0.0
        val sector =
            when (maximum) {
                red -> ((green - blue) / delta) % 6.0
                green -> (blue - red) / delta + 2.0
                else -> (red - green) / delta + 4.0
            }
        val degrees = sector * 60.0
        return if (degrees < 0) degrees + 360.0 else degrees
    }

    private fun channel(value: Double): Int = (value.coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255)
}

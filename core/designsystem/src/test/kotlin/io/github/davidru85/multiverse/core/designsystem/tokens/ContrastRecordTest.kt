package io.github.davidru85.multiverse.core.designsystem.tokens

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TEST-A11Y-002` — every text/background pair the specified B4 surfaces use meets the WCAG 2.x
 * thresholds of `REQ-UX-003` (`UI_SPEC.md` §9, `AC-REQ-UX-003-1`).
 *
 * The pair list below is the record `UI_SPEC.md` §9 references; it is the single owner of the list,
 * so a new B4 surface adds its pair here in the same change. The expected ratios are recomputed
 * from the token values, never hard-coded, so a token change that breaks contrast fails.
 *
 * Body text is at least 4.5:1 and large text at least 3:1. The tone-30 dynamic accent pairs are
 * added by `TASK-005` (`DEC-097`).
 */
class ContrastRecordTest {

    @Test
    fun `TEST-A11Y-002 given_the_b4_surfaces_when_every_pair_is_measured_then_it_meets_its_threshold`() {
        val failures = CONTRAST_PAIRS.mapNotNull { pair ->
            val measured = contrastRatio(pair.foreground, pair.background)
            if (measured < pair.minimum) {
                "${pair.surface}: ${pair.foreground.toHex()} on ${pair.background.toHex()} " +
                    "measured ${"%.2f".format(measured)} < ${pair.minimum}"
            } else {
                null
            }
        }
        assertTrue("every recorded pair must meet its threshold:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `TEST-A11Y-002 given_the_record_when_it_is_read_then_every_pair_names_its_surface_and_threshold`() {
        assertTrue("the record must not be empty", CONTRAST_PAIRS.isNotEmpty())
        CONTRAST_PAIRS.forEach { pair ->
            assertTrue("a pair must name its surface", pair.surface.isNotBlank())
            assertTrue(
                "a pair's threshold is the body (4.5) or large (3.0) value of UI_SPEC.md §9",
                pair.minimum == 4.5 || pair.minimum == 3.0,
            )
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val light = relativeLuminance(foreground)
        val dark = relativeLuminance(background)
        return (max(light, dark) + 0.05) / (min(light, dark) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun channel(component: Float): Double {
        val value = component.toDouble()
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    private fun Color.toHex(): String =
        "#%02X%02X%02X".format(
            (red * 255f).toInt(),
            (green * 255f).toInt(),
            (blue * 255f).toInt(),
        )

    /** One text/background pair a specified B4 surface renders, with its threshold. */
    private data class ContrastPair(
        val surface: String,
        val foreground: Color,
        val background: Color,
        val minimum: Double,
    )

    private companion object {
        const val BODY = 4.5
        const val LARGE = 3.0

        /** The colours and dimensions are the tokens, so a token change is measured immediately. */
        fun pair(
            surface: String,
            foreground: Color,
            background: Color,
            minimum: Double,
        ) = ContrastPair(surface, foreground, background, minimum)

        /**
         * The B4 surfaces' pairs (`UI_SPEC.md` §4.1, §6.1, §6.4, §8). Every pair is a text/background
         * combination a specified surface renders; the ratio is computed from the tokens above.
         */
        val CONTRAST_PAIRS: List<ContrastPair> = listOf(
            pair("splash wordmark \"Multiverse\" (Display Medium Emphasized)", MultiverseColors.onSurface, MultiverseColors.surface, LARGE),
            pair("splash \"EXPLORER\" (Label Large Emphasized, Primary)", MultiverseColors.primary, MultiverseColors.surface, BODY),
            pair("splash tagline (Body Medium)", MultiverseColors.onSurfaceVariant, MultiverseColors.surface, BODY),
            pair("navigation selected label (Secondary)", MultiverseColors.secondary, MultiverseColors.secondaryContainer, BODY),
            pair("navigation unselected label (On Surface Variant)", MultiverseColors.onSurfaceVariant, MultiverseColors.surfaceContainer, BODY),
            pair("card name (Title Medium Emphasized)", MultiverseColors.onSurface, MultiverseColors.surfaceContainerHigh, LARGE),
            pair("card species at 80% (Body Small)", MultiverseColors.onSurface, MultiverseColors.surfaceContainerHigh, BODY),
            pair("status badge label at 90% (Label Medium)", MultiverseColors.onSurface, MultiverseColors.surfaceContainerHighest, BODY),
            pair("placeholder heading (Display Small Emphasized)", MultiverseColors.onSurface, MultiverseColors.surface, LARGE),
            pair("placeholder body (Body Small)", MultiverseColors.onSurfaceVariant, MultiverseColors.surface, BODY),
            pair("placeholder button label (Label Large, Primary Container)", MultiverseColors.onPrimary, MultiverseColors.primary, BODY),
            pair("stat tile value, Primary Container (Headline Small Emphasized)", MultiverseColors.onPrimaryContainer, MultiverseColors.primaryContainer, LARGE),
            pair("stat tile value, Tertiary Container (Headline Small Emphasized)", MultiverseColors.onTertiaryContainer, MultiverseColors.tertiaryContainer, LARGE),
            pair("stat tile value, Secondary Fixed Dim (Headline Small Emphasized)", MultiverseColors.onSecondaryFixed, MultiverseColors.secondaryFixedDim, LARGE),
            pair("stat tile label, Primary Container (Label Medium)", MultiverseColors.onPrimaryContainer, MultiverseColors.primaryContainer, BODY),
            pair("error title (Display Small Emphasized)", MultiverseColors.onSurface, MultiverseColors.surface, LARGE),
            pair("error message (Body Medium)", MultiverseColors.onSurfaceVariant, MultiverseColors.surface, BODY),
            pair("error retry label (Label Large, Primary)", MultiverseColors.onPrimary, MultiverseColors.primary, BODY),
            pair("stale banner label (On Surface Variant on Surface Container High)", MultiverseColors.onSurfaceVariant, MultiverseColors.surfaceContainerHigh, BODY),
        )
    }
}

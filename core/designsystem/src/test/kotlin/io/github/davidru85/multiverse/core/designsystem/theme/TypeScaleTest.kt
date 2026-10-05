package io.github.davidru85.multiverse.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `TEST-UNIT-085` — the M3 Expressive type scale on the bundled Roboto Flex (`UI_SPEC.md` §3.4,
 * `DEC-103`, `DEC-137`, `TASK-113`).
 *
 * Every style used `FontFamily.Default`, the system Roboto, although the KDoc claimed a bundled Roboto
 * Flex; and `titleMedium` and `labelLarge` were overwritten with their Emphasized (Bold) variants, so
 * every list value, the detail meta and every button label rendered Bold where the spec shows Medium.
 * The Emphasized styles now live in M3 Expressive's own `*Emphasized` slots.
 */
class TypeScaleTest {
    private val type = MultiverseTypography

    private fun assertStyle(
        name: String,
        style: TextStyle,
        weight: FontWeight,
        size: Int,
        lineHeight: Int,
    ) {
        assertEquals("$name is set in the bundled Roboto Flex", RobotoFlex, style.fontFamily)
        assertEquals("$name weight", weight, style.fontWeight)
        assertEquals("$name size", size.sp, style.fontSize)
        assertEquals("$name line height", lineHeight.sp, style.lineHeight)
    }

    @Test
    fun `TEST-UNIT-085 given_the_emphasized_slots_when_read_then_they_carry_the_heavier_spec_styles`() {
        assertStyle("Display Medium Emphasized", type.displayMediumEmphasized, FontWeight.Black, 45, 52)
        assertStyle("Display Small Emphasized", type.displaySmallEmphasized, FontWeight.ExtraBold, 36, 44)
        assertStyle("Headline Small Emphasized", type.headlineSmallEmphasized, FontWeight.Bold, 24, 32)
        assertStyle("Title Medium Emphasized", type.titleMediumEmphasized, FontWeight.Bold, 16, 24)
        assertStyle("Label Large Emphasized", type.labelLargeEmphasized, FontWeight.Bold, 14, 20)
    }

    @Test
    fun `TEST-UNIT-085 given_the_baseline_slots_when_read_then_title_and_label_are_medium_again`() {
        assertStyle("Title Medium", type.titleMedium, FontWeight.Medium, 16, 24)
        assertStyle("Title Small", type.titleSmall, FontWeight.Medium, 14, 20)
        assertStyle("Label Large", type.labelLarge, FontWeight.Medium, 14, 20)
        assertStyle("Label Medium", type.labelMedium, FontWeight.Medium, 12, 16)
        assertStyle("Body Medium", type.bodyMedium, FontWeight.Normal, 14, 20)
        assertStyle("Body Small", type.bodySmall, FontWeight.Normal, 12, 16)
    }
}

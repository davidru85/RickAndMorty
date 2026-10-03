package io.github.davidru85.multiverse.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseType

/**
 * The M3 Expressive type scale of `UI_SPEC.md` §3.4 on Roboto Flex (`DEC-103`).
 *
 * Every size and line height comes from [MultiverseType], which `TEST-UNIT-035` proves equal to the
 * committed export's `Static` entries; the weights are the M3 Expressive variants the spec names
 * (Emphasized styles are the heavier ones). Sizes are `sp`, so they follow the user's font scale
 * (`REQ-UX-006`).
 *
 * The bundled family is Roboto Flex (`DEC-103`, committed as a font resource in this module); the
 * weights are the Emphasized variants of the M3 Expressive scale. Sizes are `sp`, so they follow
 * the user's font scale (`REQ-UX-006`).
 */
public val MultiverseTypography: Typography =
    Typography().run {
        copy(
            displayMedium =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Black,
                    fontSize = MultiverseType.displayMediumEmphasizedSize,
                    lineHeight = MultiverseType.displayMediumEmphasizedLineHeight,
                    letterSpacing = (-0.5).sp,
                ),
            displaySmall =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = MultiverseType.displaySmallEmphasizedSize,
                    lineHeight = MultiverseType.displaySmallEmphasizedLineHeight,
                    letterSpacing = (-0.25).sp,
                ),
            headlineSmall =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = MultiverseType.headlineSmallEmphasizedSize,
                    lineHeight = MultiverseType.headlineSmallEmphasizedLineHeight,
                ),
            titleMedium =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = MultiverseType.titleMediumEmphasizedSize,
                    lineHeight = MultiverseType.titleMediumEmphasizedLineHeight,
                    letterSpacing = 0.15.sp,
                ),
            bodyMedium =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = MultiverseType.bodyMediumSize,
                    lineHeight = MultiverseType.bodyMediumLineHeight,
                    letterSpacing = 0.25.sp,
                ),
            bodySmall =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = MultiverseType.bodySmallSize,
                    lineHeight = MultiverseType.bodySmallLineHeight,
                    letterSpacing = 0.4.sp,
                ),
            labelLarge =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = MultiverseType.labelLargeEmphasizedSize,
                    lineHeight = MultiverseType.labelLargeEmphasizedLineHeight,
                    letterSpacing = 0.1.sp,
                ),
            labelMedium =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    fontSize = MultiverseType.labelMediumSize,
                    lineHeight = MultiverseType.labelMediumLineHeight,
                    letterSpacing = 0.5.sp,
                ),
        )
    }

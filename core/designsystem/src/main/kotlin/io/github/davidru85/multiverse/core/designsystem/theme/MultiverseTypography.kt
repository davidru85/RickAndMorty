package io.github.davidru85.multiverse.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.davidru85.multiverse.core.designsystem.R
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseType

/**
 * The bundled Roboto Flex (`DEC-103`, `DEC-137`): one weight-axis variable font resource, read at each
 * weight the scale uses through its `wght` axis, so a Bold is drawn by the font rather than synthesised.
 * The resource is a Latin subset of the upstream file; its provenance and licence are recorded in
 * `DEC-137` and `core/designsystem/licenses/roboto-flex-OFL.txt`.
 */
public val RobotoFlex: FontFamily =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black).map { weight ->
            Font(
                resId = R.font.roboto_flex,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

/**
 * The M3 Expressive type scale of `UI_SPEC.md` §3.4 on Roboto Flex (`DEC-103`, `DEC-137`).
 *
 * Every slot is set in [RobotoFlex]. The baseline slots keep their M3 weights — `titleMedium`,
 * `titleSmall` and `labelLarge` are Medium — and the heavier styles the spec calls "Emphasized" live in
 * M3 Expressive's own `*Emphasized` slots, so a component asks for the emphasis it means. Sizes and line
 * heights come from [MultiverseType], which `TEST-UNIT-035` proves equal to the committed export; they
 * are `sp`, so they follow the user's font scale (`REQ-UX-006`).
 */
public val MultiverseTypography: Typography =
    Typography(fontFamily = RobotoFlex).run {
        copy(
            displayMediumEmphasized =
                TextStyle(
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.Black,
                    fontSize = MultiverseType.displayMediumEmphasizedSize,
                    lineHeight = MultiverseType.displayMediumEmphasizedLineHeight,
                    letterSpacing = (-0.5).sp,
                ),
            displaySmallEmphasized =
                TextStyle(
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = MultiverseType.displaySmallEmphasizedSize,
                    lineHeight = MultiverseType.displaySmallEmphasizedLineHeight,
                    letterSpacing = (-0.25).sp,
                ),
            headlineSmallEmphasized =
                TextStyle(
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.Bold,
                    fontSize = MultiverseType.headlineSmallEmphasizedSize,
                    lineHeight = MultiverseType.headlineSmallEmphasizedLineHeight,
                ),
            titleMediumEmphasized =
                TextStyle(
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.Bold,
                    fontSize = MultiverseType.titleMediumEmphasizedSize,
                    lineHeight = MultiverseType.titleMediumEmphasizedLineHeight,
                    letterSpacing = 0.15.sp,
                ),
            labelLargeEmphasized =
                TextStyle(
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.Bold,
                    fontSize = MultiverseType.labelLargeEmphasizedSize,
                    lineHeight = MultiverseType.labelLargeEmphasizedLineHeight,
                    letterSpacing = 0.1.sp,
                ),
            titleMedium =
                TextStyle(
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.Medium,
                    fontSize = MultiverseType.titleMediumSize,
                    lineHeight = MultiverseType.titleMediumLineHeight,
                    letterSpacing = 0.15.sp,
                ),
            bodyMedium =
                TextStyle(
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.Normal,
                    fontSize = MultiverseType.bodyMediumSize,
                    lineHeight = MultiverseType.bodyMediumLineHeight,
                    letterSpacing = 0.25.sp,
                ),
            bodySmall =
                TextStyle(
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.Normal,
                    fontSize = MultiverseType.bodySmallSize,
                    lineHeight = MultiverseType.bodySmallLineHeight,
                    letterSpacing = 0.4.sp,
                ),
            labelMedium =
                TextStyle(
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.Medium,
                    fontSize = MultiverseType.labelMediumSize,
                    lineHeight = MultiverseType.labelMediumLineHeight,
                    letterSpacing = 0.5.sp,
                ),
        )
    }

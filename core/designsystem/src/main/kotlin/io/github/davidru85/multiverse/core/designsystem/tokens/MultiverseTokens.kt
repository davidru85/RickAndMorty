package io.github.davidru85.multiverse.core.designsystem.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The design system's token objects (`DEC-022`, `DEC-102`).
//
// Every value below is the committed export of `docs/figma/tokens.json`, which `TEST-UNIT-035`
// compares in both directions: a Kotlin value that drifts from the export fails, and an export
// variable that no token maps (and is not in the test's reviewed exclusion) fails too. Figma wins
// for values (`UI_SPEC.md`), so a divergence is settled by re-exporting, never by editing a literal
// here alone.
//
// No component may carry an ad-hoc literal for a value this file names (`DESIGN.md` §3.4,
// `GUIDELINES.md` §5.2).

/** The single `Multiverse · M3 Scheme` appearance: the roles `UI_SPEC.md` §3.1 uses. */
public object MultiverseColors {
    public val primary: Color = Color(0xFFA4D661)
    public val onPrimary: Color = Color(0xFF2C4800)
    public val primaryContainer: Color = Color(0xFF497401)
    public val onPrimaryContainer: Color = Color(0xFFFFFFFF)
    public val secondary: Color = Color(0xFFB5CDB4)
    public val onSecondary: Color = Color(0xFF314532)
    public val secondaryContainer: Color = Color(0xFF2C402E)
    public val onSecondaryContainer: Color = Color(0xFFAEC5AD)
    public val tertiary: Color = Color(0xFFA68CFF)
    public val onTertiary: Color = Color(0xFF24006B)
    public val tertiaryContainer: Color = Color(0xFF997BFE)
    public val onTertiaryContainer: Color = Color(0xFF12003E)
    public val error: Color = Color(0xFFF97758)
    public val onError: Color = Color(0xFF450900)
    public val errorContainer: Color = Color(0xFF85230A)
    public val onErrorContainer: Color = Color(0xFFFF9B82)
    public val surface: Color = Color(0xFF0F0E12)
    public val onSurface: Color = Color(0xFFE9E3EF)
    public val surfaceVariant: Color = Color(0xFF27242D)
    public val onSurfaceVariant: Color = Color(0xFFAEA9B4)
    public val surfaceContainerLowest: Color = Color(0xFF000000)
    public val surfaceContainerLow: Color = Color(0xFF141318)
    public val surfaceContainer: Color = Color(0xFF1A191F)
    public val surfaceContainerHigh: Color = Color(0xFF211E26)
    public val surfaceContainerHighest: Color = Color(0xFF27242D)
    public val surfaceDim: Color = Color(0xFF0F0E12)
    public val surfaceBright: Color = Color(0xFF2D2B34)
    public val inverseSurface: Color = Color(0xFFFDF8FE)
    public val inverseOnSurface: Color = Color(0xFF575459)
    public val inversePrimary: Color = Color(0xFF426A00)
    public val outline: Color = Color(0xFF78747E)
    public val outlineVariant: Color = Color(0xFF494650)
    public val background: Color = Color(0xFF0F0E12)
    public val onBackground: Color = Color(0xFFE9E3EF)
    public val shadow: Color = Color(0xFF000000)
    public val scrim: Color = Color(0xFF000000)
    public val surfaceTint: Color = Color(0xFFA4D661)

    // The `* Fixed` / `* Fixed Dim` roles are part of the same single scheme. No B4 surface
    // renders them yet, but they are Android M3 roles of the one appearance, so they are mapped
    // rather than excluded: the parity test's exclusion list is reserved for iOS-only tokens.
    public val primaryFixed: Color = Color(0xFFBFF27A)
    public val primaryFixedDim: Color = Color(0xFFB1E46E)
    public val onPrimaryFixed: Color = Color(0xFF2B4700)
    public val onPrimaryFixedVariant: Color = Color(0xFF406600)
    public val secondaryFixed: Color = Color(0xFFD1E9CF)
    public val secondaryFixedDim: Color = Color(0xFFC3DBC1)
    public val onSecondaryFixed: Color = Color(0xFF304431)
    public val onSecondaryFixedVariant: Color = Color(0xFF4C614C)
    public val tertiaryFixed: Color = Color(0xFF997BFE)
    public val tertiaryFixedDim: Color = Color(0xFF8C6EEF)
    public val onTertiaryFixed: Color = Color(0xFF000000)
    public val onTertiaryFixedVariant: Color = Color(0xFF200060)
}

/** The brand, status and fixed-role colours (`UI_SPEC.md` §3.2). */
public object MultiverseBrandColors {
    public val portalGreen: Color = Color(0xFF97CE4C)
    public val portalGlow: Color = Color(0xFFC6FF6B)
    public val cosmicViolet: Color = Color(0xFF7B4DFF)
    public val nebulaViolet: Color = Color(0xFFB69CFF)
    public val spaceBlack: Color = Color(0xFF07060B)
    public val statusAlive: Color = Color(0xFF7EE06A)
    public val statusDead: Color = Color(0xFFFF6B6B)
    public val statusUnknown: Color = Color(0xFFB8B4BF)
}

/** Spacing and shape (`UI_SPEC.md` §3.3). */
public object MultiverseDimensions {
    public val spaceXs: Dp = 4.dp
    public val spaceS: Dp = 8.dp
    public val spaceM: Dp = 12.dp
    public val spaceL: Dp = 16.dp
    public val spaceXl: Dp = 24.dp
    public val space2Xl: Dp = 32.dp
    public val space3Xl: Dp = 48.dp

    public val cornerExtraSmall: Dp = 4.dp
    public val cornerSmall: Dp = 8.dp
    public val cornerMedium: Dp = 12.dp
    public val cornerLarge: Dp = 16.dp
    public val cornerLargeIncreased: Dp = 20.dp
    public val cornerExtraLarge: Dp = 28.dp
    public val cornerExtraLargeIncreased: Dp = 32.dp
    public val cornerExtraExtraLarge: Dp = 48.dp

    /** `Shape/Corner Full` — the Figma value 999, rendered as a fully rounded corner. */
    public val cornerFull: Dp = 999.dp
}

/**
 * The Android components' own measurements (`UI_SPEC.md` §4.1, §6, `REQ-UX-002`, `AC-REQ-UX-002-1`): the
 * sizes and insets Figma draws for one component and that no spacing or corner token names. UI code
 * takes every measurement from here or from [MultiverseDimensions], never from an inline literal, so a
 * value is changed in one place and a reviewer can trace it to its Figma node.
 */
public object MultiverseComponentDimensions {
    /** The status badge's padding, its dot and the gap between them (`StatusBadge`, Figma `16:7`). */
    public val badgePaddingHorizontal: Dp = 10.dp
    public val badgePaddingVertical: Dp = 5.dp
    public val badgeGap: Dp = 6.dp
    public val badgeDot: Dp = 8.dp

    /** The stat tile's least height and vertical padding, and a stat list row's least height. */
    public val statTileMinHeight: Dp = 76.dp
    public val statTilePaddingVertical: Dp = 10.dp
    public val statRowMinHeight: Dp = 72.dp

    /** The character card's inset, the portrait heights of the Regular and Tall variants, and its rest elevation. */
    public val cardInset: Dp = 6.dp
    public val cardPortraitRegular: Dp = 172.dp
    public val cardPortraitTall: Dp = 224.dp
    public val cardElevation: Dp = 1.dp

    /** The badge's inset from the portrait's corner on a card. */
    public val cardBadgeInset: Dp = 10.dp

    /** The empty state's illustration and the width its body text wraps at (`UI_SPEC.md` §6.4). */
    public val emptyStateIllustration: Dp = 160.dp
    public val emptyStateBodyWidth: Dp = 320.dp

    /** A Cookie-9 illustration's section icon and a section glyph's container and icon. */
    public val illustrationIcon: Dp = 64.dp
    public val sectionGlyphContainer: Dp = 48.dp
    public val icon: Dp = 24.dp

    /** The portal mark a failed portrait shows (`UI_SPEC.md` §5.3). */
    public val portraitErrorMark: Dp = 48.dp

    /** The Detail's hero, its top scrim and bottom fade, and the room the favourite action keeps clear (Figma `21:1217`). */
    public val detailHeroHeight: Dp = 468.dp
    public val detailTopScrimHeight: Dp = 160.dp
    public val detailBottomFadeHeight: Dp = 200.dp
    public val detailFabClearance: Dp = 112.dp
    public val detailTitleInset: Dp = 20.dp

    /** The Detail's controls bar and one control's container and touch target. */
    public val detailControlsBarHeight: Dp = 64.dp
    public val detailControlContainer: Dp = 40.dp
    public val detailControlTarget: Dp = 48.dp

    /** The favourite action's glow: its blur radius and its downward offset. */
    public val fabGlowRadius: Dp = 28.dp
    public val fabGlowOffset: Dp = 8.dp

    /** Discovery's app bar, the gap above its chips and above its grid, and the paging indicator. */
    public val appBarHeight: Dp = 64.dp
    public val chipsTopGap: Dp = 18.dp
    public val gridTopGap: Dp = 20.dp
    public val pagingIndicator: Dp = 48.dp

    /** The splash's Cookie-9, its portal and the portal's glow radius (`UI_SPEC.md` §6.1). */
    public val splashCookie: Dp = 240.dp
    public val splashPortal: Dp = 160.dp
    public val splashGlowRadius: Dp = 48.dp
}

/**
 * The type scale `UI_SPEC.md` §3.4 uses, on the bundled Roboto Flex (`DEC-103`).
 *
 * Sizes are `sp` so they follow the user's font scale (`REQ-UX-006`); weights come from the
 * `RobotoFlex` family declared with the font resource in `TASK-043`.
 */
public object MultiverseType {
    public val displayMediumEmphasizedSize: TextUnit = 45.sp
    public val displayMediumEmphasizedLineHeight: TextUnit = 52.sp
    public val displaySmallEmphasizedSize: TextUnit = 36.sp
    public val displaySmallEmphasizedLineHeight: TextUnit = 44.sp
    public val headlineSmallEmphasizedSize: TextUnit = 24.sp
    public val headlineSmallEmphasizedLineHeight: TextUnit = 32.sp
    public val titleMediumEmphasizedSize: TextUnit = 16.sp
    public val titleMediumEmphasizedLineHeight: TextUnit = 24.sp
    public val titleMediumSize: TextUnit = 16.sp
    public val titleMediumLineHeight: TextUnit = 24.sp
    public val bodyMediumSize: TextUnit = 14.sp
    public val bodyMediumLineHeight: TextUnit = 20.sp
    public val bodySmallSize: TextUnit = 12.sp
    public val bodySmallLineHeight: TextUnit = 16.sp
    public val labelLargeEmphasizedSize: TextUnit = 14.sp
    public val labelLargeEmphasizedLineHeight: TextUnit = 20.sp
    public val labelMediumSize: TextUnit = 12.sp
    public val labelMediumLineHeight: TextUnit = 16.sp
}

/**
 * The export variable names the token objects above map, with each token's value in export form.
 *
 * The parity test `TEST-UNIT-035` reads this map: it is the one place the Kotlin values and the
 * committed export are compared, so a new token must be added here in the same change.
 */
public object MultiverseTokens {
    private val colors: Map<String, Color> =
        mapOf(
            "Schemes/Primary" to MultiverseColors.primary,
            "Schemes/On Primary" to MultiverseColors.onPrimary,
            "Schemes/Primary Container" to MultiverseColors.primaryContainer,
            "Schemes/On Primary Container" to MultiverseColors.onPrimaryContainer,
            "Schemes/Secondary" to MultiverseColors.secondary,
            "Schemes/On Secondary" to MultiverseColors.onSecondary,
            "Schemes/Secondary Container" to MultiverseColors.secondaryContainer,
            "Schemes/On Secondary Container" to MultiverseColors.onSecondaryContainer,
            "Schemes/Tertiary" to MultiverseColors.tertiary,
            "Schemes/On Tertiary" to MultiverseColors.onTertiary,
            "Schemes/Tertiary Container" to MultiverseColors.tertiaryContainer,
            "Schemes/On Tertiary Container" to MultiverseColors.onTertiaryContainer,
            "Schemes/Error" to MultiverseColors.error,
            "Schemes/On Error" to MultiverseColors.onError,
            "Schemes/Error Container" to MultiverseColors.errorContainer,
            "Schemes/On Error Container" to MultiverseColors.onErrorContainer,
            "Schemes/Surface" to MultiverseColors.surface,
            "Schemes/On Surface" to MultiverseColors.onSurface,
            "Schemes/Surface Variant" to MultiverseColors.surfaceVariant,
            "Schemes/On Surface Variant" to MultiverseColors.onSurfaceVariant,
            "Schemes/Surface Container Lowest" to MultiverseColors.surfaceContainerLowest,
            "Schemes/Surface Container Low" to MultiverseColors.surfaceContainerLow,
            "Schemes/Surface Container" to MultiverseColors.surfaceContainer,
            "Schemes/Surface Container High" to MultiverseColors.surfaceContainerHigh,
            "Schemes/Surface Container Highest" to MultiverseColors.surfaceContainerHighest,
            "Schemes/Surface Dim" to MultiverseColors.surfaceDim,
            "Schemes/Surface Bright" to MultiverseColors.surfaceBright,
            "Schemes/Inverse Surface" to MultiverseColors.inverseSurface,
            "Schemes/Inverse On Surface" to MultiverseColors.inverseOnSurface,
            "Schemes/Inverse Primary" to MultiverseColors.inversePrimary,
            "Schemes/Outline" to MultiverseColors.outline,
            "Schemes/Outline Variant" to MultiverseColors.outlineVariant,
            "Schemes/Background" to MultiverseColors.background,
            "Schemes/On Background" to MultiverseColors.onBackground,
            "Schemes/Shadow" to MultiverseColors.shadow,
            "Schemes/Scrim" to MultiverseColors.scrim,
            "Schemes/Surface Tint" to MultiverseColors.surfaceTint,
            "Schemes/Primary Fixed" to MultiverseColors.primaryFixed,
            "Schemes/Primary Fixed Dim" to MultiverseColors.primaryFixedDim,
            "Schemes/On Primary Fixed" to MultiverseColors.onPrimaryFixed,
            "Schemes/On Primary Fixed Variant" to MultiverseColors.onPrimaryFixedVariant,
            "Schemes/Secondary Fixed" to MultiverseColors.secondaryFixed,
            "Schemes/Secondary Fixed Dim" to MultiverseColors.secondaryFixedDim,
            "Schemes/On Secondary Fixed" to MultiverseColors.onSecondaryFixed,
            "Schemes/On Secondary Fixed Variant" to MultiverseColors.onSecondaryFixedVariant,
            "Schemes/Tertiary Fixed" to MultiverseColors.tertiaryFixed,
            "Schemes/Tertiary Fixed Dim" to MultiverseColors.tertiaryFixedDim,
            "Schemes/On Tertiary Fixed" to MultiverseColors.onTertiaryFixed,
            "Schemes/On Tertiary Fixed Variant" to MultiverseColors.onTertiaryFixedVariant,
            "Brand/Portal Green" to MultiverseBrandColors.portalGreen,
            "Brand/Portal Glow" to MultiverseBrandColors.portalGlow,
            "Brand/Cosmic Violet" to MultiverseBrandColors.cosmicViolet,
            "Brand/Nebula Violet" to MultiverseBrandColors.nebulaViolet,
            "Brand/Space Black" to MultiverseBrandColors.spaceBlack,
            "Status/Alive" to MultiverseBrandColors.statusAlive,
            "Status/Dead" to MultiverseBrandColors.statusDead,
            "Status/Unknown" to MultiverseBrandColors.statusUnknown,
        )

    /**
     * The dimension tokens the UI uses, read from [MultiverseDimensions] itself rather than restated, so
     * a changed `Dp` is a changed export value and `TEST-UNIT-035` sees it.
     */
    private val floats: Map<String, Float> =
        mapOf(
            "Space/XS" to MultiverseDimensions.spaceXs.value,
            "Space/S" to MultiverseDimensions.spaceS.value,
            "Space/M" to MultiverseDimensions.spaceM.value,
            "Space/L" to MultiverseDimensions.spaceL.value,
            "Space/XL" to MultiverseDimensions.spaceXl.value,
            "Space/2XL" to MultiverseDimensions.space2Xl.value,
            "Space/3XL" to MultiverseDimensions.space3Xl.value,
            "Shape/Corner Extra Small" to MultiverseDimensions.cornerExtraSmall.value,
            "Shape/Corner Small" to MultiverseDimensions.cornerSmall.value,
            "Shape/Corner Medium" to MultiverseDimensions.cornerMedium.value,
            "Shape/Corner Large" to MultiverseDimensions.cornerLarge.value,
            "Shape/Corner Large Increased" to MultiverseDimensions.cornerLargeIncreased.value,
            "Shape/Corner Extra Large" to MultiverseDimensions.cornerExtraLarge.value,
            "Shape/Corner Extra Large Increased" to MultiverseDimensions.cornerExtraLargeIncreased.value,
            "Shape/Corner Extra Extra Large" to MultiverseDimensions.cornerExtraExtraLarge.value,
            "Shape/Corner Full" to MultiverseDimensions.cornerFull.value,
        )

    /** Every mapped token, keyed by its export variable name, with its value in export form. */
    public fun entries(): Map<String, String> =
        colors.mapValues { (_, color) -> color.toExportHex() } +
            floats.mapValues { (_, value) -> value.toExportNumber() }

    /** The export variable names the token objects map. */
    public fun mappedNames(): Set<String> = colors.keys + floats.keys

    private fun Float.toExportNumber(): String = if (this == toInt().toFloat()) toInt().toString() else toString()
}

/** A colour in the export's hex form, `#RRGGBB` or `#RRGGBBAA`: the one helper both parity records use. */
internal fun Color.toExportHex(): String {
    val red = (red * 255f).toInt()
    val green = (green * 255f).toInt()
    val blue = (blue * 255f).toInt()
    val alpha = (alpha * 255f).toInt()
    val base = "#%02X%02X%02X".format(red, green, blue)
    return if (alpha == 255) base else base + "%02X".format(alpha)
}

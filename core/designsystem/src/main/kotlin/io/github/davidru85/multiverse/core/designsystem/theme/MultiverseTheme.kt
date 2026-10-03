package io.github.davidru85.multiverse.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors

/**
 * The app's single appearance (`UI_SPEC.md` §3.1, §9, `REQ-UX-001`).
 *
 * There is exactly one colour scheme and no branching: no `isSystemInDarkTheme()`, no Material You
 * dynamic colour, and no light variant. Every role below is the `Multiverse · M3 Scheme` value, so
 * the palette is the committed export and nothing else (`DEC-022`, `DEC-102`).
 *
 * The `darkColorScheme` factory is the closest M3 constructor to this palette; the roles the app
 * renders are set explicitly, and `TEST-UNIT-035` proves the mapping equals the tokens.
 */
private val MultiverseColorScheme =
    darkColorScheme(
        primary = MultiverseColors.primary,
        onPrimary = MultiverseColors.onPrimary,
        primaryContainer = MultiverseColors.primaryContainer,
        onPrimaryContainer = MultiverseColors.onPrimaryContainer,
        inversePrimary = MultiverseColors.inversePrimary,
        secondary = MultiverseColors.secondary,
        onSecondary = MultiverseColors.onSecondary,
        secondaryContainer = MultiverseColors.secondaryContainer,
        onSecondaryContainer = MultiverseColors.onSecondaryContainer,
        tertiary = MultiverseColors.tertiary,
        onTertiary = MultiverseColors.onTertiary,
        tertiaryContainer = MultiverseColors.tertiaryContainer,
        onTertiaryContainer = MultiverseColors.onTertiaryContainer,
        background = MultiverseColors.background,
        onBackground = MultiverseColors.onBackground,
        surface = MultiverseColors.surface,
        onSurface = MultiverseColors.onSurface,
        surfaceVariant = MultiverseColors.surfaceVariant,
        onSurfaceVariant = MultiverseColors.onSurfaceVariant,
        surfaceTint = MultiverseColors.surfaceTint,
        inverseSurface = MultiverseColors.inverseSurface,
        inverseOnSurface = MultiverseColors.inverseOnSurface,
        error = MultiverseColors.error,
        onError = MultiverseColors.onError,
        errorContainer = MultiverseColors.errorContainer,
        onErrorContainer = MultiverseColors.onErrorContainer,
        outline = MultiverseColors.outline,
        outlineVariant = MultiverseColors.outlineVariant,
        scrim = MultiverseColors.scrim,
        surfaceBright = MultiverseColors.surfaceBright,
        surfaceDim = MultiverseColors.surfaceDim,
        surfaceContainer = MultiverseColors.surfaceContainer,
        surfaceContainerHigh = MultiverseColors.surfaceContainerHigh,
        surfaceContainerHighest = MultiverseColors.surfaceContainerHighest,
        surfaceContainerLow = MultiverseColors.surfaceContainerLow,
        surfaceContainerLowest = MultiverseColors.surfaceContainerLowest,
    )

/**
 * The design system's theme. One appearance, no dynamic colour (`REQ-UX-001`, `UI_SPEC.md` §9).
 *
 * The surface-container roles carry the "Space Black" depth model, and the type scale is applied
 * through [MultiverseTypography] on the bundled Roboto Flex (`DEC-103`).
 *
 * The PascalCase name is Compose's convention for a theme factory (`MaterialTheme`); the repository's
 *  scopes that naming rule to the design system.
 */
@Composable
public fun MultiverseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MultiverseColorScheme,
        typography = MultiverseTypography,
        content = content,
    )
}

/** The one colour scheme, exposed for tests and for components outside a [MultiverseTheme]. */
public object MultiverseThemeValues {
    /** The scheme [MultiverseTheme] installs. */
    public val colorScheme = MultiverseColorScheme

    /**
     * The role values as their export names and hex forms, so `TEST-UNIT-035` can prove the theme
     * maps the tokens and not a second palette.
     */
    public fun roleValues(): Map<String, String> = ROLE_VALUES

    /** The export-form hex of a token colour, so the role record compares with the export. */
    private fun Color.exportHex(): String {
        val red = (red * 255f).toInt()
        val green = (green * 255f).toInt()
        val blue = (blue * 255f).toInt()
        val alpha = (alpha * 255f).toInt()
        val base = "#%02X%02X%02X".format(red, green, blue)
        return if (alpha == 255) base else base + "%02X".format(alpha)
    }

    private fun hex(color: Color): String = color.exportHex()

    private val ROLE_VALUES: Map<String, String> =
        buildMap {
            put("Schemes/Primary", hex(MultiverseColors.primary))
            put("Schemes/On Primary", hex(MultiverseColors.onPrimary))
            put("Schemes/Primary Container", hex(MultiverseColors.primaryContainer))
            put("Schemes/On Primary Container", hex(MultiverseColors.onPrimaryContainer))
            put("Schemes/Secondary", hex(MultiverseColors.secondary))
            put("Schemes/On Secondary", hex(MultiverseColors.onSecondary))
            put("Schemes/Secondary Container", hex(MultiverseColors.secondaryContainer))
            put("Schemes/On Secondary Container", hex(MultiverseColors.onSecondaryContainer))
            put("Schemes/Tertiary", hex(MultiverseColors.tertiary))
            put("Schemes/On Tertiary", hex(MultiverseColors.onTertiary))
            put("Schemes/Tertiary Container", hex(MultiverseColors.tertiaryContainer))
            put("Schemes/On Tertiary Container", hex(MultiverseColors.onTertiaryContainer))
            put("Schemes/Error", hex(MultiverseColors.error))
            put("Schemes/On Error", hex(MultiverseColors.onError))
            put("Schemes/Error Container", hex(MultiverseColors.errorContainer))
            put("Schemes/On Error Container", hex(MultiverseColors.onErrorContainer))
            put("Schemes/Surface", hex(MultiverseColors.surface))
            put("Schemes/On Surface", hex(MultiverseColors.onSurface))
            put("Schemes/Surface Variant", hex(MultiverseColors.surfaceVariant))
            put("Schemes/On Surface Variant", hex(MultiverseColors.onSurfaceVariant))
            put("Schemes/Surface Container Lowest", hex(MultiverseColors.surfaceContainerLowest))
            put("Schemes/Surface Container Low", hex(MultiverseColors.surfaceContainerLow))
            put("Schemes/Surface Container", hex(MultiverseColors.surfaceContainer))
            put("Schemes/Surface Container High", hex(MultiverseColors.surfaceContainerHigh))
            put("Schemes/Surface Container Highest", hex(MultiverseColors.surfaceContainerHighest))
            put("Schemes/Surface Dim", hex(MultiverseColors.surfaceDim))
            put("Schemes/Surface Bright", hex(MultiverseColors.surfaceBright))
            put("Schemes/Inverse Surface", hex(MultiverseColors.inverseSurface))
            put("Schemes/Inverse On Surface", hex(MultiverseColors.inverseOnSurface))
            put("Schemes/Inverse Primary", hex(MultiverseColors.inversePrimary))
            put("Schemes/Outline", hex(MultiverseColors.outline))
            put("Schemes/Outline Variant", hex(MultiverseColors.outlineVariant))
        }
}

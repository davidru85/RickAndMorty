package io.github.davidru85.multiverse.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseTokens
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `TEST-UNIT-035`, the theme half: the `ColorScheme` [MultiverseTheme] installs carries the token
 * values, not a second palette (`REQ-UX-002`, `DEC-022`, `DEC-102`).
 *
 * A role that drifts from its token fails here, so a component cannot read a colour the committed
 * export does not hold.
 */
class ThemeTokenMappingTest {
    private val scheme: ColorScheme = MultiverseThemeValues.colorScheme

    @Test
    fun `TEST-UNIT-035 given_the_theme_when_each_role_is_read_then_it_equals_its_token`() {
        assertEquals("primary", MultiverseColors.primary, scheme.primary)
        assertEquals("onPrimary", MultiverseColors.onPrimary, scheme.onPrimary)
        assertEquals("primaryContainer", MultiverseColors.primaryContainer, scheme.primaryContainer)
        assertEquals("onPrimaryContainer", MultiverseColors.onPrimaryContainer, scheme.onPrimaryContainer)
        assertEquals("secondary", MultiverseColors.secondary, scheme.secondary)
        assertEquals("onSecondary", MultiverseColors.onSecondary, scheme.onSecondary)
        assertEquals("secondaryContainer", MultiverseColors.secondaryContainer, scheme.secondaryContainer)
        assertEquals("onSecondaryContainer", MultiverseColors.onSecondaryContainer, scheme.onSecondaryContainer)
        assertEquals("tertiary", MultiverseColors.tertiary, scheme.tertiary)
        assertEquals("onTertiary", MultiverseColors.onTertiary, scheme.onTertiary)
        assertEquals("tertiaryContainer", MultiverseColors.tertiaryContainer, scheme.tertiaryContainer)
        assertEquals("onTertiaryContainer", MultiverseColors.onTertiaryContainer, scheme.onTertiaryContainer)
        assertEquals("error", MultiverseColors.error, scheme.error)
        assertEquals("onError", MultiverseColors.onError, scheme.onError)
        assertEquals("errorContainer", MultiverseColors.errorContainer, scheme.errorContainer)
        assertEquals("onErrorContainer", MultiverseColors.onErrorContainer, scheme.onErrorContainer)
        assertEquals("surface", MultiverseColors.surface, scheme.surface)
        assertEquals("onSurface", MultiverseColors.onSurface, scheme.onSurface)
        assertEquals("surfaceVariant", MultiverseColors.surfaceVariant, scheme.surfaceVariant)
        assertEquals("onSurfaceVariant", MultiverseColors.onSurfaceVariant, scheme.onSurfaceVariant)
        assertEquals("surfaceContainerLowest", MultiverseColors.surfaceContainerLowest, scheme.surfaceContainerLowest)
        assertEquals("surfaceContainerLow", MultiverseColors.surfaceContainerLow, scheme.surfaceContainerLow)
        assertEquals("surfaceContainer", MultiverseColors.surfaceContainer, scheme.surfaceContainer)
        assertEquals("surfaceContainerHigh", MultiverseColors.surfaceContainerHigh, scheme.surfaceContainerHigh)
        assertEquals("surfaceContainerHighest", MultiverseColors.surfaceContainerHighest, scheme.surfaceContainerHighest)
        assertEquals("surfaceDim", MultiverseColors.surfaceDim, scheme.surfaceDim)
        assertEquals("surfaceBright", MultiverseColors.surfaceBright, scheme.surfaceBright)
        assertEquals("inverseSurface", MultiverseColors.inverseSurface, scheme.inverseSurface)
        assertEquals("inverseOnSurface", MultiverseColors.inverseOnSurface, scheme.inverseOnSurface)
        assertEquals("inversePrimary", MultiverseColors.inversePrimary, scheme.inversePrimary)
        assertEquals("outline", MultiverseColors.outline, scheme.outline)
        assertEquals("outlineVariant", MultiverseColors.outlineVariant, scheme.outlineVariant)
        assertEquals("scrim", MultiverseColors.scrim, scheme.scrim)
        assertEquals("surfaceTint", MultiverseColors.surfaceTint, scheme.surfaceTint)
    }

    @Test
    fun `TEST-UNIT-035 given_the_theme_when_the_record_is_read_then_every_entry_is_a_mapped_token`() {
        val roles = MultiverseThemeValues.roleValues()
        val mapped = MultiverseTokens.entries()
        assertEquals("the record covers every role the theme installs from a token", 32, roles.size)
        roles.forEach { (name, value) ->
            assertEquals("a role must name its export variable: $name", true, name.startsWith("Schemes/"))
            assertEquals("a role must equal its mapped token: $name", value, mapped[name])
        }
    }
}

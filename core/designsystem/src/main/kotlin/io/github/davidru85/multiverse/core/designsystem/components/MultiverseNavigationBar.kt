package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors

/** One destination of the app-wide navigation bar (`UI_SPEC.md` §4.1). */
public data class NavigationDestination(
    /** A stable identity the shell maps back to its own route type; never rendered. */
    public val key: String,
    public val label: String,
    public val icon: ImageVector,
    /** The outlined variant of [icon], used while the destination is unselected. */
    public val unselectedIcon: ImageVector,
)

/**
 * The app-wide navigation bar (`UI_SPEC.md` §4.1): four destinations in a fixed order, with the
 * selected item's colours bound once, here, and never overridden by a screen.
 *
 * The component knows nothing about navigation: the shell passes the destinations and receives the
 * one the user tapped, so no feature names another and the bar stays reusable by every top-level
 * surface (`DEC-013`, `REQ-FUNC-008`).
 *
 * Item colours are the spec's single binding: selected Secondary Container indicator with On
 * Secondary Container icon and Secondary label; unselected On Surface Variant icon and label.
 */
@Composable
public fun MultiverseNavigationBar(
    destinations: List<NavigationDestination>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** The label of the bar as a whole, for the merged semantics node. */
    barDescription: String? = null,
) {
    Surface(
        color = MultiverseColors.surfaceContainer,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = false) {
                    role = Role.Tab
                    barDescription?.let { contentDescription = it }
                },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            destinations.forEach { destination ->
                val selected = destination.key == selectedKey
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination.key) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.icon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MultiverseColors.onSecondaryContainer,
                            selectedTextColor = MultiverseColors.secondary,
                            indicatorColor = MultiverseColors.secondaryContainer,
                            unselectedIconColor = MultiverseColors.onSurfaceVariant,
                            unselectedTextColor = MultiverseColors.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

/** The 24 dp gesture-inset area the Figma component adds below the bar (`UI_SPEC.md` §1.2). */
@Composable
public fun NavigationBarGestureInset(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout
        .Box(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp))
}

/** A painter-shaped overload for a caller whose icons are painters rather than vectors. */
@Composable
public fun NavigationBarIcon(
    painter: Painter,
    modifier: Modifier = Modifier,
) {
    Icon(painter = painter, contentDescription = null, modifier = modifier)
}

@Preview
@Composable
private fun NavigationBarPreview() {
    MultiverseTheme {
        MultiverseNavigationBar(
            destinations =
                listOf(
                    NavigationDestination("characters", "Characters", PreviewIcons.Dot, PreviewIcons.Dot),
                    NavigationDestination("episodes", "Episodes", PreviewIcons.Dot, PreviewIcons.Dot),
                    NavigationDestination("favorites", "Favorites", PreviewIcons.Dot, PreviewIcons.Dot),
                    NavigationDestination("settings", "Settings", PreviewIcons.Dot, PreviewIcons.Dot),
                ),
            selectedKey = "characters",
            onSelect = {},
        )
    }
}

/** A 24 dp filled dot, so the preview needs no icon artifact. */
private object PreviewIcons {
    private val Outline: ImageVector =
        ImageVector
            .Builder(
                name = "PreviewDot",
                defaultWidth =
                    androidx.compose.ui.unit
                        .Dp(24f),
                defaultHeight =
                    androidx.compose.ui.unit
                        .Dp(24f),
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill =
                        androidx.compose.ui.graphics
                            .SolidColor(androidx.compose.ui.graphics.Color.White),
                ) {
                    moveTo(6f, 6f)
                    lineTo(18f, 6f)
                    lineTo(18f, 18f)
                    lineTo(6f, 18f)
                    close()
                }
            }.build()

    val Dot: ImageVector = Outline
}

package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.davidru85.multiverse.core.designsystem.layout.MultiverseGrid
import io.github.davidru85.multiverse.core.designsystem.preview.MultiverseComponentPreviews
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions

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
                // A group of tabs, not a tab itself: only the items carry `Role.Tab` (`UI_SPEC.md` §9).
                .selectableGroup()
                .semantics(mergeDescendants = false) {
                    barDescription?.let { contentDescription = it }
                },
    ) {
        val rows = if (MultiverseGrid.columnsFor(LocalDensity.current.fontScale) == 1) destinations.chunked(2) else listOf(destinations)
        Column {
            rows.forEach { rowDestinations ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MultiverseDimensions.spaceXs),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowDestinations.forEach { destination ->
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
            // The container extends behind the system gesture area, as Figma `117:887` draws it
            // (412 × 88 including the inset), instead of leaving it to the screen's own surface.
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@MultiverseComponentPreviews
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

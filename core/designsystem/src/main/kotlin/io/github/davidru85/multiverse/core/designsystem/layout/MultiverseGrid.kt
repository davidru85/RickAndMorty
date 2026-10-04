package io.github.davidru85.multiverse.core.designsystem.layout

import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity

/**
 * The column count of the character grids as a function of the user's font scale (`REQ-UX-006`,
 * `AC-REQ-UX-006-1`).
 *
 * The requirement is that grids drop to one column at the largest accessibility sizes, so the number
 * of columns is a property of the platform text setting rather than a constant. Discovery and
 * Favorites share this one definition so the two grids cannot disagree, and the rule is testable
 * without a rendered screen because it is a pure function of the scale.
 */
public object MultiverseGrid {
    /**
     * The font scale at which the grid collapses to one column.
     *
     * Android's accessibility sizes start above 1.0 and reach 2.0; 1.5 is the first step at which two
     * 184 dp cards plus their gutter stop fitting a portrait phone, so it is where "the largest
     * accessibility sizes" begins to bind.
     */
    public const val SINGLE_COLUMN_FONT_SCALE: Float = 1.5f

    /** The column count for [fontScale]: two at the standard sizes, one from [SINGLE_COLUMN_FONT_SCALE]. */
    public fun columnsFor(fontScale: Float): Int = if (fontScale >= SINGLE_COLUMN_FONT_SCALE) 1 else 2

    /** The column strategy for the current configuration's font scale. */
    @Composable
    @ReadOnlyComposable
    public fun columns(): StaggeredGridCells = StaggeredGridCells.Fixed(columnsFor(LocalDensity.current.fontScale))
}

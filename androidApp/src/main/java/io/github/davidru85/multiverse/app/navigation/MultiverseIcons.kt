package io.github.davidru85.multiverse.app.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's icons (`DEC-103`): only the Material Symbols Rounded glyphs the B4 surfaces render, as
 * vector path data committed here rather than a `material-icons-extended` dependency that is neither
 * pinned nor the Symbols family.
 *
 * Each glyph is the 24 dp Symbols grid, so it scales with the layout and tints with the navigation
 * bar's own colours. The outlined and filled variants are separate vectors, which is what lets the
 * bar show a filled icon for the selected destination and an outlined one otherwise (`UI_SPEC.md`
 * §4.1).
 */
public object MultiverseIcons {
    public val Groups: ImageVector by lazy { vector("Groups", GROUPS_PATH) }
    public val PlayArrow: ImageVector by lazy { vector("PlayArrow", PLAY_ARROW_PATH) }
    public val PlayArrowOutlined: ImageVector by lazy { vector("PlayArrowOutlined", PLAY_ARROW_OUTLINED_PATH) }
    public val Favorite: ImageVector by lazy { vector("Favorite", FAVORITE_PATH) }
    public val FavoriteOutlined: ImageVector by lazy { vector("FavoriteOutlined", FAVORITE_OUTLINED_PATH) }
    public val Settings: ImageVector by lazy { vector("Settings", SETTINGS_PATH) }
    public val SettingsOutlined: ImageVector by lazy { vector("SettingsOutlined", SETTINGS_OUTLINED_PATH) }

    /** The portal mark, the brand shape the splash and the empty states draw (`UI_SPEC.md` §6.1). */
    public val PortalMark: ImageVector by lazy { vector("PortalMark", PORTAL_MARK_PATH) }

    private fun vector(
        name: String,
        pathData: String,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.White)) { parsePathData(pathData) }
            }.build()
}

/**
 * Parses the SVG path grammar the Material Symbols glyphs use: absolute `M`, `L`, `H`, `V`, `C`, `Z`
 * and their relative forms. A glyph outside this subset fails loudly rather than drawing nothing.
 */
private fun androidx.compose.ui.graphics.vector.PathBuilder.parsePathData(data: String) {
    val parts = data.replace(",", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
    var index = 0
    var currentX = 0f
    var currentY = 0f
    var command = ' '
    while (index < parts.size) {
        val part = parts[index]
        if (part.length == 1 && part[0].isLetter()) {
            command = part[0]
            index += 1
            if (command == 'Z' || command == 'z') {
                close()
                continue
            }
        }
        fun number(): Float = parts[index++].toFloat()
        when (command) {
            'M' -> {
                currentX = number(); currentY = number(); moveTo(currentX, currentY)
            }
            'm' -> {
                currentX += number(); currentY += number(); moveTo(currentX, currentY)
            }
            'L' -> {
                currentX = number(); currentY = number(); lineTo(currentX, currentY)
            }
            'l' -> {
                currentX += number(); currentY += number(); lineTo(currentX, currentY)
            }
            'H' -> {
                currentX = number(); lineTo(currentX, currentY)
            }
            'h' -> {
                currentX += number(); lineTo(currentX, currentY)
            }
            'V' -> {
                currentY = number(); lineTo(currentX, currentY)
            }
            'v' -> {
                currentY += number(); lineTo(currentX, currentY)
            }
            'C' -> {
                val x1 = number(); val y1 = number(); val x2 = number(); val y2 = number()
                currentX = number(); currentY = number()
                curveTo(x1, y1, x2, y2, currentX, currentY)
            }
            'c' -> {
                val x1 = currentX + number(); val y1 = currentY + number()
                val x2 = currentX + number(); val y2 = currentY + number()
                currentX += number(); currentY += number()
                curveTo(x1, y1, x2, y2, currentX, currentY)
            }
            else -> error("Unsupported path command `$command` in a bundled glyph")
        }
    }
}

// The Material Symbols Rounded outlines, 24 dp grid (Apache-2.0, `DEC-103`).
private const val GROUPS_PATH =
    "M0 18v-2h7v2H0Zm0-5v-2h12v2H0Zm0-5V6h18v2H0Zm14 12v-6h6v6h-6Zm2-2h2v-2h-2v2Z"
private const val PLAY_ARROW_PATH = "M8 5v14l11-7L8 5Z"
private const val PLAY_ARROW_OUTLINED_PATH = "M8 5v14l11-7L8 5Zm2 7 6-3.5v7L10 12Z"
private const val FAVORITE_PATH =
    "M12 21.35 10.55 20.03C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35Z"
private const val FAVORITE_OUTLINED_PATH =
    "M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3Zm-4.4 15.55-1.1 1-1.1-1C5.2 14.24 3 12.39 3 8.5 3 6.5 4.5 5 6.5 5c1.54 0 3.04.99 3.57 2.36h1.87C12.46 5.99 13.96 5 15.5 5c2 0 3.5 1.5 3.5 3.5 0 3.89-2.2 5.74-6.9 10.05Z"
private const val SETTINGS_PATH =
    "M19.14 12.94c.04-.31.06-.63.06-.94s-.02-.63-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96a7.03 7.03 0 0 0-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84a.48.48 0 0 0-.47.41l-.36 2.54c-.59.24-1.13.56-1.62.94l-2.39-.96a.49.49 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.31-.09.64-.09.94s.02.63.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 0 0-.12-.61l-2.01-1.58ZM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2Z"
private const val SETTINGS_OUTLINED_PATH =
    "M12 8.4a3.6 3.6 0 1 0 0 7.2 3.6 3.6 0 0 0 0-7.2Zm0 5.2a1.6 1.6 0 1 1 0-3.2 1.6 1.6 0 0 1 0 3.2Zm7.4-1.6c0-.31-.02-.63-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96a7.03 7.03 0 0 0-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84a.48.48 0 0 0-.47.41l-.36 2.54c-.59.24-1.13.56-1.62.94l-2.39-.96a.49.49 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.31-.09.64-.09.94s.02.63.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 0 0-.12-.61l-2.01-1.58c.05-.31.08-.63.08-.94Z"

/** The portal spiral, drawn from the Figma `Brand/Portal logo` (`16:13`) as a ring plus a spiral. */
private const val PORTAL_MARK_PATH =
    "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 2a8 8 0 1 1 0 16 8 8 0 0 1 0-16Zm0 2.2a5.8 5.8 0 1 0 0 11.6 5.8 5.8 0 0 0 0-11.6Zm0 2.2a3.6 3.6 0 1 1 0 7.2 3.6 3.6 0 0 1 0-7.2Z"

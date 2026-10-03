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
 * Parses the SVG path grammar the Material Symbols glyphs and the portal mark use: absolute `M`, `L`,
 * `H`, `V`, `C`, `Z` and their relative forms, with the numbers that follow a command repeated until
 * the next command letter.
 *
 * The tokenizer is whitespace- and comma-tolerant, and it treats a minus sign and a decimal point as
 * number starts even when they are not separated by whitespace (`h-2` is one command and one number),
 * which is the form the Symbols data uses. A glyph outside this subset fails loudly rather than
 * drawing nothing, so a mistyped path is a crash in the first test that renders it, not a silent hole.
 */
private fun androidx.compose.ui.graphics.vector.PathBuilder.parsePathData(data: String) {
    val parts = tokenizePath(data)
    var index = 0
    var currentX = 0f
    var currentY = 0f
    var command = ' '
    var lastControlX = 0f
    var lastControlY = 0f
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
        fun number(): Float {
            val value = parts[index].toFloat()
            index += 1
            return value
        }
        when (command) {
            'M' -> { currentX = number(); currentY = number(); moveTo(currentX, currentY) }
            'm' -> { currentX += number(); currentY += number(); moveTo(currentX, currentY) }
            'L' -> { currentX = number(); currentY = number(); lineTo(currentX, currentY) }
            'l' -> { currentX += number(); currentY += number(); lineTo(currentX, currentY) }
            'H' -> { currentX = number(); lineTo(currentX, currentY) }
            'h' -> { currentX += number(); lineTo(currentX, currentY) }
            'V' -> { currentY = number(); lineTo(currentX, currentY) }
            'v' -> { currentY += number(); lineTo(currentX, currentY) }
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
            'S' -> {
                // A smooth cubic: the first control point mirrors the previous curve's second one.
                val x2 = number(); val y2 = number()
                currentX = number(); currentY = number()
                curveTo(2 * currentX - lastControlX, 2 * currentY - lastControlY, x2, y2, currentX, currentY)
            }
            's' -> {
                val x2 = currentX + number(); val y2 = currentY + number()
                val endX = currentX + number(); val endY = currentY + number()
                curveTo(2 * currentX - lastControlX, 2 * currentY - lastControlY, x2, y2, endX, endY)
                currentX = endX; currentY = endY
            }
            'Q' -> {
                // A quadratic `(start, control, end)` is the cubic whose controls sit two thirds of
                // the way from each end towards the quadratic's control point.
                val controlX = number(); val controlY = number()
                val endX = number(); val endY = number()
                curveTo(
                    currentX + 2f / 3f * (controlX - currentX),
                    currentY + 2f / 3f * (controlY - currentY),
                    endX + 2f / 3f * (controlX - endX),
                    endY + 2f / 3f * (controlY - endY),
                    endX,
                    endY,
                )
                currentX = endX; currentY = endY
            }
            'q' -> {
                val controlX = currentX + number(); val controlY = currentY + number()
                val endX = currentX + number(); val endY = currentY + number()
                curveTo(
                    currentX + 2f / 3f * (controlX - currentX),
                    currentY + 2f / 3f * (controlY - currentY),
                    endX + 2f / 3f * (controlX - endX),
                    endY + 2f / 3f * (controlY - endY),
                    endX,
                    endY,
                )
                currentX = endX; currentY = endY
            }
            'a' -> {
                val radiusX = number(); val radiusY = number(); number()
                val largeArc = number() != 0f
                val sweep = number() != 0f
                val endX = currentX + number(); val endY = currentY + number()
                arcTo(currentX, currentY, radiusX, radiusY, largeArc, sweep, endX, endY)
                currentX = endX; currentY = endY
            }
            'A' -> {
                val radiusX = number(); val radiusY = number(); number()
                val largeArc = number() != 0f
                val sweep = number() != 0f
                val endX = number(); val endY = number()
                arcTo(currentX, currentY, radiusX, radiusY, largeArc, sweep, endX, endY)
                currentX = endX; currentY = endY
            }
            else -> error("Unsupported path command `$command` in a bundled glyph")
        }
    }
}

/**
 * Appends the elliptical arc of the SVG grammar as cubic segments (`F.6` of the SVG specification):
 * the endpoint form is converted to a centre parameterisation, then each sweep of at most 90 degrees
 * becomes one cubic. An arc whose radii are zero is the straight line the grammar defines.
 */
private fun androidx.compose.ui.graphics.vector.PathBuilder.arcTo(
    startX: Float,
    startY: Float,
    radiusX: Float,
    radiusY: Float,
    largeArc: Boolean,
    sweep: Boolean,
    endX: Float,
    endY: Float,
) {
    if (radiusX == 0f || radiusY == 0f || (startX == endX && startY == endY)) {
        lineTo(endX, endY)
        return
    }
    val phi = 0.0
    val rx = kotlin.math.abs(radiusX.toDouble())
    val ry = kotlin.math.abs(radiusY.toDouble())
    val dx2 = (startX - endX) / 2.0
    val dy2 = (startY - endY) / 2.0
    // Rotate into the ellipse's own frame (`phi` is zero for these glyphs, so the rotation is identity).
    val x1p = dx2
    val y1p = dy2
    val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    val scale = if (lambda > 1.0) kotlin.math.sqrt(lambda) else 1.0
    val rxs = rx * scale
    val rys = ry * scale
    val sign = if (largeArc != sweep) 1.0 else -1.0
    val numerator = (rxs * rxs * rys * rys) - (rxs * rxs * y1p * y1p) - (rys * rys * x1p * x1p)
    val denominator = (rxs * rxs * y1p * y1p) + (rys * rys * x1p * x1p)
    val coef = sign * kotlin.math.sqrt((numerator / denominator).coerceAtLeast(0.0))
    val cxp = coef * (rxs * y1p / rys)
    val cyp = coef * (-rys * x1p / rxs)
    val cx = cxp + (startX + endX) / 2.0
    val cy = cyp + (startY + endY) / 2.0
    fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val len = kotlin.math.sqrt(ux * ux + uy * uy) * kotlin.math.sqrt(vx * vx + vy * vy)
        val value = (dot / len).coerceIn(-1.0, 1.0)
        val signed = if (ux * vy - uy * vx < 0) -1.0 else 1.0
        return signed * kotlin.math.acos(value)
    }
    val thetaStart = angle(1.0, 0.0, (x1p - cxp) / rxs, (y1p - cyp) / rys)
    val thetaSweep =
        angle((x1p - cxp) / rxs, (y1p - cyp) / rys, (-x1p - cxp) / rxs, (-y1p - cyp) / rys)
    val effectiveSweep = if (!sweep && thetaSweep > 0) thetaSweep - 2 * kotlin.math.PI else if (sweep && thetaSweep < 0) thetaSweep + 2 * kotlin.math.PI else thetaSweep
    val segments = kotlin.math.ceil(kotlin.math.abs(effectiveSweep) / (kotlin.math.PI / 2)).toInt().coerceAtLeast(1)
    val delta = effectiveSweep / segments
    var theta = thetaStart
    var previousX = startX.toDouble()
    var previousY = startY.toDouble()
    repeat(segments) {
        val next = theta + delta
        val alpha = (4.0 / 3.0) * kotlin.math.tan(delta / 4.0)
        val cosTheta = kotlin.math.cos(theta)
        val sinTheta = kotlin.math.sin(theta)
        val cosNext = kotlin.math.cos(next)
        val sinNext = kotlin.math.sin(next)
        val endPointX = cx + rxs * cosNext
        val endPointY = cy + rys * sinNext
        val control1X = previousX + alpha * (-rxs * sinTheta)
        val control1Y = previousY + alpha * (rys * cosTheta)
        val control2X = endPointX - alpha * (-rxs * sinNext)
        val control2Y = endPointY - alpha * (rys * cosNext)
        curveTo(control1X.toFloat(), control1Y.toFloat(), control2X.toFloat(), control2Y.toFloat(), endPointX.toFloat(), endPointY.toFloat())
        previousX = endPointX
        previousY = endPointY
        theta = next
    }
}

/**
 * Splits a path into command letters and numbers. A letter is its own token; a number starts at a
 * digit, a minus sign or a dot and runs while the characters can belong to one number, so `-2.5.5`
 * yields `-2.5` and `.5` the way the grammar means.
 */
private fun tokenizePath(data: String): List<String> {
    val parts = mutableListOf<String>()
    var index = 0
    while (index < data.length) {
        val c = data[index]
        when {
            c.isWhitespace() || c == ',' -> index += 1
            c.isLetter() -> { parts += c.toString(); index += 1 }
            else -> {
                val start = index
                if (data[index] == '+' || data[index] == '-') index += 1
                var seenDot = false
                while (index < data.length) {
                    val digit = data[index]
                    if (digit.isDigit()) {
                        index += 1
                    } else if (digit == '.' && !seenDot) {
                        seenDot = true
                        index += 1
                    } else {
                        break
                    }
                }
                parts += data.substring(start, index)
            }
        }
    }
    return parts
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
    "M10 2h4l.5 2.2 2.1-.9 2.8 2.8-.9 2.1L21 10v4l-2.2.5.9 2.1-2.8 2.8-2.1-.9L14 22h-4l-.5-2.2-2.1.9-2.8-2.8.9-2.1L2 14v-4l2.2-.5-.9-2.1 2.8-2.8 2.1.9L10 2Zm2 7.6a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8Z"
private const val SETTINGS_OUTLINED_PATH =
    "M10 2h4l.5 2.2 2.1-.9 2.8 2.8-.9 2.1L21 10v4l-2.2.5.9 2.1-2.8 2.8-2.1-.9L14 22h-4l-.5-2.2-2.1.9-2.8-2.8.9-2.1L2 14v-4l2.2-.5-.9-2.1 2.8-2.8 2.1.9L10 2Zm2 5.6a4.4 4.4 0 1 0 0 8.8 4.4 4.4 0 0 0 0-8.8Zm0 2.4a2 2 0 1 1 0 4 2 2 0 0 1 0-4Z"
/** The portal spiral, drawn from the Figma `Brand/Portal logo` (`16:13`) as a ring plus a spiral. */
private const val PORTAL_MARK_PATH =
    "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 2a8 8 0 1 1 0 16 8 8 0 0 1 0-16Zm0 2.2a5.8 5.8 0 1 0 0 11.6 5.8 5.8 0 0 0 0-11.6Zm0 2.2a3.6 3.6 0 1 1 0 7.2 3.6 3.6 0 0 1 0-7.2Z"

package io.github.davidru85.multiverse.app.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.davidru85.multiverse.core.designsystem.components.NavigationDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * `TEST-UNIT-108` — the navigation bar draws the Material Symbols Rounded glyphs Figma `117:887` uses
 * (`TASK-130`, `GAP-040`, `UI_SPEC.md` §4.1): `groups`, `play_arrow`, `favorite` and `settings`, filled
 * for the selected destination and outlined otherwise.
 *
 * The references are the official SVGs, copied verbatim from `google/material-design-icons`
 * (`symbols/web/<name>/materialsymbolsrounded/`, Apache-2.0) into the test resources. Each bar glyph and
 * its reference are rasterised the same way — the vector's own groups and paths onto one canvas — so a
 * glyph passes only when it draws the official shape. The bar's earlier hand-drawn paths (three bars and
 * a square for Characters, a corrupted heart) fail.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain `Application`: the case renders vectors and needs no graph.
@Config(sdk = [36], qualifiers = "en", application = android.app.Application::class)
class MaterialSymbolsGlyphTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TEST-UNIT-108 given_the_navigation_destinations_when_their_glyphs_render_then_each_is_its_material_symbol`() {
        var destinations: List<NavigationDestination> = emptyList()
        compose.setContent { destinations = topLevelDestinations() }
        compose.waitForIdle()
        assertEquals("the bar has its four destinations", 4, destinations.size)

        val mismatches =
            destinations.flatMap { destination ->
                val (selected, unselected) = SYMBOLS.getValue(destination.key)
                listOfNotNull(
                    mismatch("${destination.key} selected", destination.icon, selected),
                    mismatch("${destination.key} unselected", destination.unselectedIcon, unselected),
                )
            }

        assertTrue("TEST-UNIT-108: every bar glyph is its Material Symbol:\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    /** A description of how [glyph] differs from the official [symbol], or `null` when it draws it. */
    private fun mismatch(
        label: String,
        glyph: ImageVector,
        symbol: String,
    ): String? {
        val drawn = alphaMask(glyph)
        val official = alphaMask(officialSymbol(symbol))
        val differing = drawn.indices.count { abs(drawn[it] - official[it]) > ALPHA_TOLERANCE }
        return if (differing == 0) null else "$label differs from Material Symbols Rounded `$symbol` in $differing of ${drawn.size} pixels"
    }

    /** The official glyph, parsed from its SVG resource with the SVG's own view box. */
    private fun officialSymbol(name: String): ImageVector {
        val svg = requireNotNull(javaClass.getResource("/material-symbols/${name}_24px.svg")) { "missing reference `$name`" }.readText()
        val (minX, minY, width, height) =
            Regex("viewBox=\"([^\"]+)\"")
                .find(svg)!!
                .groupValues[1]
                .split(' ')
                .map(String::toFloat)
        val pathData = Regex(" d=\"([^\"]+)\"").find(svg)!!.groupValues[1]
        return ImageVector
            .Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = width, viewportHeight = height)
            .addGroup(translationX = -minX, translationY = -minY)
            .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
            .clearGroup()
            .build()
    }

    /** The glyph's alpha at [MASK_PX] × [MASK_PX], drawn from its own groups and paths. */
    private fun alphaMask(vector: ImageVector): IntArray {
        val bitmap = Bitmap.createBitmap(MASK_PX, MASK_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(MASK_PX / vector.viewportWidth, MASK_PX / vector.viewportHeight)
        draw(canvas, vector.root)
        val pixels = IntArray(MASK_PX * MASK_PX)
        bitmap.getPixels(pixels, 0, MASK_PX, 0, 0, MASK_PX, MASK_PX)
        return IntArray(pixels.size) { pixels[it] ushr 24 }
    }

    private fun draw(
        canvas: Canvas,
        group: VectorGroup,
    ) {
        canvas.save()
        canvas.translate(group.translationX, group.translationY)
        group.forEach { node ->
            when (node) {
                is VectorGroup -> draw(canvas, node)
                is VectorPath -> canvas.drawPath(node.pathData.toPath().asAndroidPath(), PAINT)
            }
        }
        canvas.restore()
    }

    private companion object {
        /** Destination key → (selected, unselected) official glyph (`UI_SPEC.md` §4.1, Figma `117:887`). */
        val SYMBOLS =
            mapOf(
                KEY_CHARACTERS to ("groups_fill1" to "groups"),
                KEY_EPISODES to ("play_arrow_fill1" to "play_arrow"),
                KEY_FAVORITES to ("favorite_fill1" to "favorite"),
                KEY_SETTINGS to ("settings_fill1" to "settings"),
            )

        /** Twice the 24 dp grid, enough pixels for a wrong contour to show. */
        const val MASK_PX = 48

        /** Anti-aliasing noise allowed per pixel, out of 255. */
        const val ALPHA_TOLERANCE = 8

        val PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    }
}

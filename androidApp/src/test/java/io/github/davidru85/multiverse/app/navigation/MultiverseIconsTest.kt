package io.github.davidru85.multiverse.app.navigation

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `TEST-UNIT-058` — every bundled glyph parses (`DEC-103`, `TASK-044`).
 *
 * The icons are path data rather than an icon dependency, so a mistyped path is a defect only a
 * renderer would notice. This case parses each glyph the way the navigation bar does, so a bad path
 * fails in `check` instead of at first launch — which is exactly how the shell's first launch run
 * found the two commands the parser was missing.
 *
 * A glyph that parses to nothing fails too, so an empty or whitespace path cannot pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiverseIconsTest {
    @Test
    fun `TEST-UNIT-058 given_every_bundled_glyph_when_it_is_parsed_then_it_yields_a_drawable_vector`() {
        val glyphs =
            mapOf(
                "Groups" to MultiverseIcons.Groups,
                "PlayArrow" to MultiverseIcons.PlayArrow,
                "PlayArrowOutlined" to MultiverseIcons.PlayArrowOutlined,
                "Favorite" to MultiverseIcons.Favorite,
                "FavoriteOutlined" to MultiverseIcons.FavoriteOutlined,
                "Settings" to MultiverseIcons.Settings,
                "SettingsOutlined" to MultiverseIcons.SettingsOutlined,
                "PortalMark" to MultiverseIcons.PortalMark,
            )
        glyphs.forEach { (name, vector) ->
            assertTrue("$name must parse with a non-empty canvas", vector.defaultWidth.value > 0f)
            val path = (vector.root.first() as androidx.compose.ui.graphics.vector.VectorPath)
            assertTrue("$name must produce at least one path node", path.pathData.isNotEmpty())
        }
    }
}

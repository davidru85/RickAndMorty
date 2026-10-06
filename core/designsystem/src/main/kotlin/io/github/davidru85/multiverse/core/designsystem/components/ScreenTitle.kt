package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.davidru85.multiverse.core.designsystem.preview.MultiverseComponentPreviews
import io.github.davidru85.multiverse.core.designsystem.preview.MultiversePreviewSurface
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions

/**
 * A top-level screen's title (`UI_SPEC.md` §6.2, §6.4, §6.5; Figma `20:1842`, `101:499`, `101:637`,
 * `101:568`): Display Small Emphasized in On Surface, and a heading, so a screen reader can move to
 * it. The screen places it 16 dp from its edges. Characters, Episodes, Favorites and Settings use this one component,
 * so their titles cannot drift apart.
 */
@Composable
public fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmallEmphasized,
        color = MultiverseColors.onSurface,
        modifier = modifier.fillMaxWidth().semantics { heading() },
    )
}

@MultiverseComponentPreviews
@Composable
private fun ScreenTitlePreview() {
    MultiversePreviewSurface {
        ScreenTitle(text = "Characters", modifier = Modifier.padding(MultiverseDimensions.spaceL))
    }
}

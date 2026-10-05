package io.github.davidru85.multiverse.core.designsystem.image

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter

/**
 * The brand portal mark the shell provides once for every portrait and error surface (`UI_SPEC.md`
 * §5.3, §8, `AC-REQ-FUNC-005-2`). The asset is the shell's, so the design system names no drawable of
 * the app; `null` outside the shell, where a failed portrait shows its container alone.
 */
public val LocalPortalMark: ProvidableCompositionLocal<Painter?> = staticCompositionLocalOf { null }

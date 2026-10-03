package io.github.davidru85.multiverse.core.designsystem.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * The portrait **seam** (`DEC-097`, [ADR-0015](../../../../../../../docs/adr/0015-image-pipeline-and-accent-placement.md)).
 *
 * `:core:designsystem` owns the portrait's rendering states, but it may declare Compose only
 * (`R15`) and no project dependency (`R4`), so it cannot name Coil, Ktor or a platform loader. It
 * therefore declares this interface in Compose terms — a URL in, a painter or a failure out — and
 * the composition root implements it over the allow-listed image client.
 *
 * The requested [widthPx] and [heightPx] are the decode size, never a claim about the source
 * resolution (`AC-REQ-FUNC-005-3`); the URL is the cache key verbatim (`IC-016`).
 */
public interface ImageSeam {
    /**
     * Loads [url] as a painter, or reports a failure the caller renders as the portal-mark error
     * state. It never throws for a network failure; a cancelled call propagates unchanged.
     */
    @Composable
    public fun rememberPainter(
        url: String,
        widthPx: Int,
        heightPx: Int,
    ): ImageSeamResult
}

/** What the seam returned for one URL (`UI_SPEC.md` §5.1, §5.3). */
public sealed interface ImageSeamResult {
    /** The image is not available yet; the caller renders the placeholder. */
    public data object Loading : ImageSeamResult

    /** The painter to draw, already at the requested decode size. */
    public data class Success(
        public val painter: Painter,
    ) : ImageSeamResult

    /** The load failed; the caller renders the portal-mark error state, never a broken-image glyph. */
    public data object Failure : ImageSeamResult
}

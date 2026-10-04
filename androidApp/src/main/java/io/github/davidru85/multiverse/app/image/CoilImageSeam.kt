package io.github.davidru85.multiverse.app.image

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult

/**
 * The shell's implementation of the design system's portrait seam (`DEC-097`, ADR-0015).
 *
 * `:core:designsystem` owns the portrait's states but may declare Compose only (`R15`), so the
 * transport lives here: Coil over the **same allow-listed Ktor client** the data path uses (`TASK-021`
 * wires the cache budget), keyed by the image URL verbatim, which is the cache key `IC-016` requires.
 *
 * The requested decode size is the one the component asked for — never above the 300 px the
 * specification allows — and the crossfade is Coil's own, with the design system's duration, so the
 * placeholder and the failure state stay the component's business.
 */
public class CoilImageSeam(
    private val context: Context,
    private val imageLoader: ImageLoader,
) : ImageSeam {
    @Composable
    override fun rememberPainter(
        url: String,
        widthPx: Int,
        heightPx: Int,
    ): ImageSeamResult {
        val request =
            remember(url, widthPx, heightPx, imageLoader) {
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(Size(widthPx, heightPx))
                    .crossfade(
                        durationMillis = io.github.davidru85.multiverse.core.designsystem.components.PortraitCrossfadeMillis,
                    ).build()
            }
        val painter = rememberAsyncImagePainter(model = request, imageLoader = imageLoader)
        // Coil 3 exposes the load state as a `StateFlow`; reading it as Compose state keeps the
        // component's own recomposition contract, so the seam reports the state instead of observing
        // it twice.
        val state by painter.state.collectAsState()
        return when (state) {
            is AsyncImagePainter.State.Success -> ImageSeamResult.Success(painter)
            is AsyncImagePainter.State.Error -> ImageSeamResult.Failure
            else -> ImageSeamResult.Loading
        }
    }
}


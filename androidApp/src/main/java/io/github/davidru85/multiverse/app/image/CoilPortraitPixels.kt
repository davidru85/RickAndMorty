package io.github.davidru85.multiverse.app.image

import android.content.Context
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.davidru85.multiverse.core.designsystem.image.PortraitPixels

/**
 * A portrait's pixels for its accent (`UI_SPEC.md` §5.4, `DEC-097`), read through the app's one image
 * loader, so the request takes the allow-listed client and the caches every portrait uses (`SECURITY.md`
 * §5): a portrait already on screen is read from the cache, not fetched again.
 *
 * The request asks for a small **software** bitmap — a hardware bitmap cannot be read (§5.4 step 5) —
 * sized for the quantizer rather than for display, so the extraction stays cheap. A portrait that fails
 * yields `null`, and the policy then falls back to Portal Green.
 */
public class CoilPortraitPixels(
    private val context: Context,
    private val imageLoader: ImageLoader,
) : PortraitPixels {
    override suspend fun pixelsFor(imageUrl: String): IntArray? {
        val request =
            ImageRequest
                .Builder(context)
                .data(imageUrl)
                .size(ACCENT_SAMPLE_PX)
                .allowHardware(false)
                .build()
        val result = imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = result.image.toBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    private companion object {
        /** The sampled portrait's longest side: enough colours for the quantizer, cheap to decode. */
        const val ACCENT_SAMPLE_PX = 64
    }
}

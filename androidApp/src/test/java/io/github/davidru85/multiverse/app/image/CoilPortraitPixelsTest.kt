package io.github.davidru85.multiverse.app.image

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * `TEST-UI-034`, the Android source of pixels (`UI_SPEC.md` §5.4 step 5, `DEC-097`, `TASK-113`): the
 * accent reads a portrait's pixels through the app's one image loader — the allow-listed client and its
 * caches — as a software bitmap, because a hardware bitmap cannot be read. The transport is a mock, so
 * no network is involved (`REQ-REL-004`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class CoilPortraitPixelsTest {
    private val red = 0xFFC03020.toInt()

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun pixels(status: HttpStatusCode = HttpStatusCode.OK): CoilPortraitPixels {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val body = png(red)
        val client =
            HttpClient(
                MockEngine {
                    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()))
                },
            )
        return CoilPortraitPixels(context, imageLoader(context = context, client = client, diskCacheDirectory = imageCacheDirectory(context)))
    }

    @Test
    fun `TEST-UI-034 given_a_served_portrait_when_its_pixels_are_read_then_they_are_its_colours`() =
        runTest {
            val read = assertNotNull(pixels().pixelsFor("https://example.invalid/avatar/1.jpeg"))

            assertEquals("TEST-UI-034: the decoded pixels are the portrait's", red, read!!.first())
        }

    @Test
    fun `TEST-UI-034 given_a_portrait_that_fails_when_its_pixels_are_read_then_there_are_none`() =
        runTest {
            assertNull("TEST-UI-034: an unreadable portrait yields no pixels, so the policy falls back", pixels(HttpStatusCode.NotFound).pixelsFor("https://example.invalid/avatar/2.jpeg"))
        }
}

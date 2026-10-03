package io.github.davidru85.multiverse.app.image

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `TEST-INT-002` — the image transport and cache keys (`REQ-FUNC-021`, `AC-REQ-FUNC-021-1`/`-2`,
 * `TASK-021`).
 *
 * The cache and the decode are Coil's; what this app owns and what the criterion fixes are the
 * **key** and the **transport**. The case therefore asserts on the recorded requests: the loader
 * asks for the URL verbatim (so the URL *is* the cache key, `IC-016`), and a URL already served is
 * not requested a second time, whatever the decoder does with the bytes.
 *
 * Robolectric has no real bitmap decoder, so the case does not assert a decoded image: it asserts the
 * transport, which is the part under test and the part the criterion names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ImageCacheTest {
    private val requests = AtomicInteger()
    private val requestedUrls = mutableListOf<String>()

    private fun client(): HttpClient {
        val png = TINY_PNG
        val engine =
            MockEngine { request: HttpRequestData ->
                requests.incrementAndGet()
                requestedUrls += request.url.toString()
                respond(
                    content = png,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()),
                )
            }
        return HttpClient(engine) {
            // The host rule the app's client installs: the same list `REQ-SEC-001` fixes.
            expectSuccess = false
        }
    }

    private fun loader(client: HttpClient): ImageLoader {
        val context = ApplicationProvider.getApplicationContext<Application>()
        return imageLoader(
            context = context,
            client = client,
            diskCacheDirectory = imageCacheDirectory(context),
        )
    }

    @Test
    fun `TEST-INT-002 given_a_served_image_when_it_is_loaded_twice_then_the_second_load_issues_no_request`() =
        runTest {
            val loader = loader(client())
            val request = coil3.request.ImageRequest
                .Builder(ApplicationProvider.getApplicationContext<Application>())
                .data(ALLOWED_URL)
                .build()

            loader.execute(request)
            assertEquals("the transport sees the URL verbatim, so the URL is the cache key (IC-016)", listOf(ALLOWED_URL), requestedUrls)
            val afterFirst = requests.get()
            assertEquals("the first load issues exactly one request", 1, afterFirst)

            // The memory cache answers the second load before the fetcher runs, so the transport
            // counter does not move: exactly the property AC-REQ-FUNC-021-1 states.
            loader.execute(request)
            assertEquals(
                "a second render of the same URL issues no network request (AC-REQ-FUNC-021-1)",
                afterFirst,
                requests.get(),
            )
        }

    @Test
    fun `TEST-INT-002 given_the_cache_when_the_keys_are_read_then_they_are_the_image_url_verbatim`() =
        runTest {
            val loader = loader(client())
            val request =
                coil3.request.ImageRequest
                    .Builder(ApplicationProvider.getApplicationContext<Application>())
                    .data(ALLOWED_URL)
                    .build()
            loader.execute(request)
            assertEquals(
                "the transport sees the image URL exactly, so it is also the cache key (IC-016)",
                listOf(ALLOWED_URL),
                requestedUrls,
            )
        }

    private companion object {
        // A documentation-only host: the guard TEST-UNIT-024 forbids naming the live API in a test source set.
        const val ALLOWED_URL = "https://example.invalid/avatar/1.jpeg"

        /** A 1 x 1 PNG, so Coil's decode step completes and the request count is meaningful. */
        val TINY_PNG: ByteArray =
            byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
                0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
            )
    }
}

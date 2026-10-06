package io.github.davidru85.multiverse.app.sound

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.davidru85.multiverse.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `TEST-UNIT-110` — the Android selection sound is the low-latency form `DEC-162` names, and it is loaded
 * before the first tap (`REQ-FUNC-036`, `AC-REQ-FUNC-036-4`, `TASK-139`).
 *
 * The resource is Ogg Vorbis, mono, at 48 kHz, and its length shows the owner's recording without the
 * silence around it: the source lasts 0.758 s, and the sound itself about 0.664 s. `SoundPool` decodes the
 * file once, when the player is built, so a tap plays PCM that is already in memory; before that decode
 * has completed a tap plays nothing rather than waiting for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BundledSelectionSoundTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun resourceBytes(): ByteArray = context.resources.openRawResource(R.raw.selection_sound).use { it.readBytes() }

    @Test
    fun `TEST-UNIT-110 given_the_bundled_sound_when_its_headers_are_read_then_it_is_trimmed_mono_ogg_vorbis_at_48_khz`() {
        val bytes = resourceBytes()

        assertEquals("TEST-UNIT-110: an Ogg stream", "OggS", String(bytes, 0, 4, Charsets.US_ASCII))
        // The Vorbis identification header: packet type 1, "vorbis", version, channels, sample rate.
        val header = bytes.indexOf(byteArrayOf(1) + "vorbis".toByteArray(Charsets.US_ASCII))
        assertTrue("TEST-UNIT-110: the stream is Vorbis", header >= 0)
        val channels = bytes[header + 11].toInt() and 0xFF
        val sampleRate = bytes.littleEndian(header + 12, 4)
        assertEquals("TEST-UNIT-110: mono", 1, channels)
        assertEquals("TEST-UNIT-110: at the 48 kHz most devices mix at", 48_000L, sampleRate)

        // The last page's granule position is the stream's length in samples.
        val lastPage = bytes.lastIndexOf("OggS".toByteArray(Charsets.US_ASCII))
        val seconds = bytes.littleEndian(lastPage + 6, 8).toDouble() / sampleRate
        assertTrue(
            "TEST-UNIT-110: the leading and trailing silence is gone, the sound is whole (${seconds}s)",
            seconds in 0.664..0.700,
        )
    }

    @Test
    fun `TEST-UNIT-110 given_the_player_when_it_is_built_then_it_loads_the_sound_and_plays_it_once_loaded`() {
        val pool = sonificationPool()
        val sound = SoundPoolSelectionSound(context, pool)
        val shadow = shadowOf(pool)

        sound.play()
        assertTrue(
            "TEST-UNIT-110: a tap before the decode completes plays nothing",
            shadow.getResourcePlaybacks(R.raw.selection_sound).isEmpty(),
        )

        shadow.notifyResourceLoaded(R.raw.selection_sound, true)
        sound.play()
        assertEquals(
            "TEST-UNIT-110: the bundled sound was loaded when the player was built, and plays",
            1,
            shadow.getResourcePlaybacks(R.raw.selection_sound).size,
        )
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int =
        (0..size - needle.size).firstOrNull { start -> needle.indices.all { this[start + it] == needle[it] } } ?: -1

    private fun ByteArray.lastIndexOf(needle: ByteArray): Int =
        (size - needle.size downTo 0).firstOrNull { start -> needle.indices.all { this[start + it] == needle[it] } } ?: -1

    private fun ByteArray.littleEndian(
        offset: Int,
        length: Int,
    ): Long = (0 until length).fold(0L) { value, i -> value or ((this[offset + i].toLong() and 0xFF) shl (8 * i)) }
}

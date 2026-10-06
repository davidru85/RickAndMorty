package io.github.davidru85.multiverse.app.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import io.github.davidru85.multiverse.app.R

/**
 * The bundled selection sound, played through `SoundPool` (`AC-REQ-FUNC-036-4`, `DEC-162`).
 *
 * `res/raw/selection_sound.ogg` is decoded into memory once, when the player is built, so a tap plays PCM
 * that is already loaded instead of opening and decoding a file. Until that decode completes a tap plays
 * nothing: the sound is feedback, and a late one would be worse than none.
 *
 * The pool holds one sound and one stream, so a tap while the sound is playing restarts it rather than
 * layering a second one. It lives as long as the process, like the graph that holds it.
 */
public class SoundPoolSelectionSound(
    context: Context,
    private val pool: SoundPool = sonificationPool(),
) : SelectionSound {
    @Volatile
    private var loaded = false

    private val soundId: Int

    init {
        // Set before `load`: the pool reports a completion only to a listener it already has. It holds
        // this one sound, so any successful completion is this sound's.
        pool.setOnLoadCompleteListener { _, _, status -> loaded = status == LOAD_SUCCEEDED }
        soundId = pool.load(context, R.raw.selection_sound, PRIORITY)
    }

    override fun play() {
        if (loaded) pool.play(soundId, FULL_VOLUME, FULL_VOLUME, PRIORITY, NO_LOOP, NORMAL_RATE)
    }

    private companion object {
        const val LOAD_SUCCEEDED = 0
        const val PRIORITY = 1
        const val FULL_VOLUME = 1f
        const val NO_LOOP = 0
        const val NORMAL_RATE = 1f
    }
}

/**
 * A one-stream pool whose output is a **sonification**, the usage of interface sounds: the system plays
 * it at the volume of its own interface sounds and mutes it in silent and vibrate modes, so the app never
 * sounds when the phone is silenced (`DEC-163`).
 */
internal fun sonificationPool(): SoundPool =
    SoundPool
        .Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ).build()

package io.github.davidru85.multiverse.app.navigation

import android.content.Context
import android.content.Intent
import io.github.davidru85.multiverse.core.data.remote.RickAndMortyApi
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.core.presentation.CopyKeys

/**
 * The Detail's Share (`DEC-125`, `UI_SPEC.md` §6.3): one line — the character's name and its API
 * resource URL — through the system chooser, so the user picks the target app.
 *
 * The line is the shared copy key `share_character_text`, resolved from the one Android copy set, and
 * the URL comes from `:core:data`'s configured host, so no UI source names a host.
 */
internal fun shareCharacterIntent(
    context: Context,
    card: CharacterCardUi,
): Intent {
    val text = context.getString(resource(CopyKeys.SHARE_CHARACTER_TEXT.value), card.name, RickAndMortyApi.characterUrl(card.id.value))
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
    return Intent.createChooser(send, context.getString(resource(CopyKeys.ACTION_SHARE.value)))
}

/** The resource behind a registered copy key; an unregistered one is a defect the parity test catches. */
private fun resource(key: String): Int =
    requireNotNull(CopyResolver.resourceId(key)) { "no Android resource is registered for the copy key `$key` (DEC-100)" }

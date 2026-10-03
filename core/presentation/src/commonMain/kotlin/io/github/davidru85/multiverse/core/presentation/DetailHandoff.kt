package io.github.davidru85.multiverse.core.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterId

/**
 * The shared navigation hand-off (`IC-025`, `DESIGN.md` §4.2).
 *
 * The card a user taps in Discovery must be on the Detail screen **before the network responds**, so
 * the hero can animate from the card's bounds and the known fields can render immediately
 * (`REQ-FUNC-002`, `AC-REQ-FUNC-002-1`). Neither feature may depend on the other (ADR-0001), so the
 * payload travels here: Discovery publishes one [CharacterCardUi], the Detail screen consumes the one
 * matching the id it was routed with, and the shell owns the single instance.
 *
 * It holds **no copy of a list** and no network state: a screen reads it once on entry. The port is
 * deliberately small, because it is the only coupling between the two features.
 */
public class DetailHandoff {
    private var pending: CharacterCardUi? = null

    /** Publishes the card the user just selected. Called by Discovery before it navigates. */
    public fun publish(card: CharacterCardUi) {
        pending = card
    }

    /**
     * The card for [id], or `null` when the hand-off holds none or holds another character's. A deep
     * link and a process restart both land here with nothing to consume, which is why the Detail
     * screen renders from its own state when this returns `null`.
     */
    public fun consume(id: CharacterId): CharacterCardUi? = pending?.takeIf { it.id == id }

    /** Drops the payload, so a later detail entry cannot render a stale header. */
    public fun clear() {
        pending = null
    }
}

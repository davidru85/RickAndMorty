package io.github.davidru85.multiverse.core.domain.model

import kotlin.jvm.JvmInline

// `IC-001` (`API_SPECS.md` §3): an id is the canonical server string in every layer. REST integer
// ids are converted with `toString()` exactly once, in the mapper, and an id is never derived from
// a position. Value-class identity is the only identity: no trimming, case folding or coercion.

/** The canonical id of a character. */
@JvmInline
public value class CharacterId(
    public val value: String,
)

/** The canonical id of a location. */
@JvmInline
public value class LocationId(
    public val value: String,
)

/** The canonical id of an episode. */
@JvmInline
public value class EpisodeId(
    public val value: String,
)

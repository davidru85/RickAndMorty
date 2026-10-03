package io.github.davidru85.multiverse.core.data.cache

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol

/** Stub: one key for every request, so identity is not yet established (`REQ-REL-001`). */
internal object CacheKeyBuilder {
    fun page(
        protocol: RemoteProtocol,
        filter: CharacterFilter,
        page: Int,
    ): CacheKey = CacheKey("page")

    fun details(
        protocol: RemoteProtocol,
        id: CharacterId,
        enrich: Boolean,
    ): CacheKey = CacheKey("details")
}

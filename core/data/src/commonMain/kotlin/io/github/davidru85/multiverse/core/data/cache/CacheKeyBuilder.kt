package io.github.davidru85.multiverse.core.data.cache

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.RemoteProtocol
import io.github.davidru85.multiverse.core.domain.model.StatusFilter

/**
 * The one builder of a [CacheKey] (`IC-012`, `adr/0005-caching-strategy.md`).
 *
 * The key is the complete normalized request identity, so two filter combinations, two pages or the
 * same logical request over REST and over GraphQL can never share an entry (`REQ-REL-001`,
 * `AC-REQ-REL-001-1`, `AC-REQ-FUNC-034-4`):
 *
 * ```
 * key = protocol + "|" + method + "|" + pathTemplate + "|" + canonicalQuery
 * ```
 *
 * The canonical query writes its parameters in a fixed alphabetical order, omits a blank value
 * entirely and lower-cases the values the wire contract fixes (`status`), so "the query the adapter
 * sends" and "the key the cache computes" cannot drift (`API_SPECS.md` §7.1).
 */
internal object CacheKeyBuilder {
    /** The key of one character-list page request. */
    fun page(
        protocol: RemoteProtocol,
        filter: CharacterFilter,
        page: Int,
    ): CacheKey {
        val query = filter.query.trim()
        val parameters =
            buildList {
                add("page" to page.toString())
                if (query.isNotEmpty()) add("name" to query)
                wireStatus(filter.status)?.let { add("status" to it) }
            }
        return key(protocol, method(protocol), characterListPath(protocol), canonicalQuery(parameters))
    }

    /** The key of one character-detail request; `enrich` decides whether the enriched value is stored. */
    fun details(
        protocol: RemoteProtocol,
        id: CharacterId,
        enrich: Boolean,
    ): CacheKey =
        key(
            protocol,
            method(protocol),
            detailsPath(protocol, id),
            canonicalQuery(if (enrich) listOf("enrich" to "true") else emptyList()),
        )

    private fun key(
        protocol: RemoteProtocol,
        method: String,
        path: String,
        query: String,
    ): CacheKey = CacheKey("${protocol.wireName()}|$method|$path|$query")

    private fun method(protocol: RemoteProtocol): String =
        when (protocol) {
            RemoteProtocol.Rest -> "GET"
            RemoteProtocol.GraphQl -> "POST"
        }

    private fun characterListPath(protocol: RemoteProtocol): String =
        when (protocol) {
            RemoteProtocol.Rest -> "/api/character"
            RemoteProtocol.GraphQl -> "/graphql"
        }

    private fun detailsPath(
        protocol: RemoteProtocol,
        id: CharacterId,
    ): String =
        when (protocol) {
            RemoteProtocol.Rest -> "/api/character/${id.value}"
            RemoteProtocol.GraphQl -> "/graphql"
        }

    /** Parameters in a fixed alphabetical order, URL-encoded once, with blank values omitted. */
    private fun canonicalQuery(parameters: List<Pair<String, String>>): String =
        parameters
            .filter { (_, value) -> value.isNotEmpty() }
            .sortedBy { (name, _) -> name }
            .joinToString("&") { (name, value) -> "$name=${encode(value)}" }

    /** The `status` value the adapter sends; `All` sends nothing (`CharacterFilter`). */
    private fun wireStatus(status: StatusFilter): String? =
        when (status) {
            StatusFilter.All -> null
            StatusFilter.Alive -> "alive"
            StatusFilter.Dead -> "dead"
            StatusFilter.Unknown -> "unknown"
        }

    private fun encode(value: String): String =
        buildString {
            value.encodeToByteArray().forEach { byte ->
                val code = byte.toInt().toChar()
                if (code.isLetterOrDigit() || code in UNRESERVED) append(code) else append('%').append(byte.toHex())
            }
        }

    private fun Byte.toHex(): String {
        val hex = "0123456789ABCDEF"
        val value = toInt() and 0xFF
        return "${hex[value shr 4]}${hex[value and 0x0F]}"
    }

    private const val UNRESERVED = "-._~"

    /** The protocol's key form; fixed here so REST and GraphQL entries can never collide. */
    private fun RemoteProtocol.wireName(): String =
        when (this) {
            RemoteProtocol.Rest -> "rest"
            RemoteProtocol.GraphQl -> "graphql"
        }
}

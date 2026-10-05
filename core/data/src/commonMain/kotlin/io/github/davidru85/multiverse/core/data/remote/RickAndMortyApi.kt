package io.github.davidru85.multiverse.core.data.remote

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * The remote host as a build constant (`REQ-SEC-001`, `SECURITY.md` §5.1): every request is HTTPS
 * to [HOST], and nothing in a response may change where the app connects.
 */
public object RickAndMortyApi {
    /** The one host the app talks to; never discovered from a response. */
    public const val HOST: String = "rickandmortyapi.com"

    internal const val API: String = "api"
    internal const val CHARACTER: String = "character"
    internal const val LOCATION: String = "location"
    internal const val EPISODE: String = "episode"

    /** The GraphQL path segment (`API_SPECS.md` §5.1); the endpoint is `POST https://[HOST]/graphql`. */
    internal const val GRAPHQL: String = "graphql"

    /**
     * An HTTPS URL on [HOST] for `/api/<segments>`. The segments are already encoded: callers pass
     * fixed resource names and validated decimal ids, which need no escaping.
     */
    internal fun url(
        vararg segments: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ): Url =
        URLBuilder(protocol = URLProtocol.HTTPS, host = HOST)
            .apply {
                encodedPathSegments = listOf(API) + segments
                parameters.forEach { (name, value) -> this.parameters.append(name, value) }
            }.build()

    /** The one GraphQL endpoint, `POST` to the same allow-listed host (`API_SPECS.md` §5.1). */
    internal fun graphQl(): Url =
        URLBuilder(protocol = URLProtocol.HTTPS, host = HOST)
            .apply { encodedPathSegments = listOf(GRAPHQL) }
            .build()

    /** Whether [url] may be trusted as a relation or pagination link: HTTPS on [HOST], default port. */
    internal fun isAllowListed(url: Url): Boolean =
        url.protocol == URLProtocol.HTTPS && url.host == HOST && url.port == URLProtocol.HTTPS.defaultPort

    /**
     * Whether [url] — a portrait URL exactly as a payload carries it — may be fetched at all
     * (`REQ-SEC-001`, `DEC-126`): HTTPS, [HOST] exactly (no sub-domain), its default port and no user
     * info. A string that does not parse is rejected. Android's loader gets the same rule from the
     * allow-listed client; the iOS pipeline has no such client, so it asks this before any of its
     * layers touches the URL, and the rule exists once.
     */
    public fun isAllowedImageUrl(url: String): Boolean {
        val parsed =
            try {
                Url(url)
            } catch (_: Exception) {
                return false
            }
        return isAllowListed(parsed) && parsed.user == null && parsed.password == null
    }
}

/** The resource names an `ApiFailure.NotFound` carries for this API. */
public object RemoteResources {
    public const val CHARACTER: String = "character"
    public const val CHARACTER_PAGE: String = "character-page"
    public const val EPISODE: String = "episode"
}

/** The codes of the `ApiWarning`s a remote adapter produces. */
public object RemoteWarnings {
    /** A requested resource the response omitted; the warning's detail is the missing id. */
    public const val MISSING_RESOURCE: String = "missing-resource"

    /**
     * A usable GraphQL response that arrived with `errors` (`API_SPECS.md` §6.2 step 5, `API-ERR-011`).
     * The data renders, and the warning keeps the partial response out of every cache
     * (`AC-REQ-FUNC-020-3`).
     */
    public const val PARTIAL_RESPONSE: String = "partial-response"

    /**
     * A detail whose requested enrichment failed: it renders without the dependent rows and is never
     * written to a cache (`ERROR_FLOW.md` §7).
     */
    public const val ENRICHMENT_FAILED: String = "enrichment-failed"
}

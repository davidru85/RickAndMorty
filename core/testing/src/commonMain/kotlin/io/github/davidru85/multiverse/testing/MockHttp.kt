package io.github.davidru85.multiverse.testing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * The fixture-driven network seam (`TESTING.md` §4.1, §4.2; `TASK-024`).
 *
 * A test never opens a socket: the remote boundary is Ktor `MockEngine`, and its responses come
 * from the committed fixtures through [FixtureLoader]. Inlining a body here would drift from the
 * captured API shape silently, which is the failure the fixtures exist to prevent.
 *
 * The harness exposes the raw [HttpClient] so a subject under test can be constructed with it, and
 * records the requests it served so a test can assert what the code asked for.
 */
public object MockHttp {
    /** One request the harness served, reduced to the fields a test legitimately asserts on. */
    public data class Served(
        public val method: String,
        public val url: String,
        public val body: String,
    )

    /**
     * A client whose engine serves [routes].
     *
     * @param routes a predicate per request; the first match wins. A request that matches nothing
     *   fails the test loudly rather than returning an empty body, because a silently unmocked
     *   call would make the subject under test look correct.
     */
    public fun client(vararg routes: Route): Pair<HttpClient, MutableList<Served>> {
        val served = mutableListOf<Served>()
        val engine =
            MockEngine { request ->
                served += request.served()
                val route =
                    routes.firstOrNull { it.matches(request) }
                        ?: error(
                            "No fixture route matched ${request.method.value} ${request.url}. " +
                                "Add a route that serves a committed fixture rather than inlining a body.",
                        )
                respond(route)
            }
        return HttpClient(engine) to served
    }

    /**
     * A client that answers successive requests with [routes] **in order**, each once, for a test that
     * scripts a sequence — a `500` and then a `200`, say. A request that does not match the next route,
     * or one past the end of the script, fails the test loudly rather than receiving an invented body.
     */
    public fun sequence(vararg routes: Route): Pair<HttpClient, MutableList<Served>> {
        val served = mutableListOf<Served>()
        val script = ArrayDeque(routes.toList())
        val engine =
            MockEngine { request ->
                served += request.served()
                val route =
                    script.removeFirstOrNull()
                        ?: error("The scripted sequence is exhausted at ${request.method.value} ${request.url}.")
                check(route.matches(request)) {
                    "Request ${served.size} (${request.method.value} ${request.url}) does not match its scripted route."
                }
                respond(route)
            }
        return HttpClient(engine) to served
    }

    private suspend fun HttpRequestData.served() =
        Served(method = method.value, url = url.toString(), body = body.toByteArray().decodeToString())

    private fun MockRequestHandleScope.respond(route: Route) =
        respond(
            content = route.body,
            status = HttpStatusCode.fromValue(route.status),
            headers =
                headersOf(
                    *(route.headers + ("content-type" to route.contentType))
                        .map { (name, value) -> name to listOf(value) }
                        .toTypedArray(),
                ),
        )

    /**
     * One fixture-backed response.
     *
     * @param fixture the committed fixture file name, resolved through [FixtureLoader].
     * @param status the HTTP status the fixture was captured with; the sidecar records it, and a
     *   route that disagrees with its sidecar is a defect the caller should see.
     * @param headers extra response headers, for the cases whose behaviour depends on one (a
     *   `Retry-After` on a `429`, for example). The body still comes from the fixture.
     */
    public fun route(
        fixture: String,
        method: String = "GET",
        urlContains: String = "",
        status: Int? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
    ): Route {
        val meta = FixtureCatalog.meta(fixture)
        return Route(
            method = method,
            urlContains = urlContains,
            body = FixtureLoader.text(fixture),
            status = status ?: meta.status,
            contentType = contentType,
            headers = headers,
        )
    }

    /** A [route] whose fixture is served with an explicit status, for error-path assertions. */
    public fun errorRoute(
        fixture: String,
        status: Int,
        method: String = "GET",
        urlContains: String = "",
        headers: Map<String, String> = emptyMap(),
    ): Route = route(fixture = fixture, method = method, urlContains = urlContains, status = status, headers = headers)

    /** A predicate plus the response it serves. */
    public data class Route(
        public val method: String,
        public val urlContains: String,
        public val body: String,
        public val status: Int,
        public val contentType: String,
        public val headers: Map<String, String> = emptyMap(),
    ) {
        public fun matches(request: HttpRequestData): Boolean =
            request.method.value.equals(method, ignoreCase = true) &&
                (urlContains.isEmpty() || request.url.toString().contains(urlContains))
    }
}

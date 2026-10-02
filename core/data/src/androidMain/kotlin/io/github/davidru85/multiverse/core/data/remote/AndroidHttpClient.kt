package io.github.davidru85.multiverse.core.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient

/**
 * The Android client: OkHttp under Ktor (`DEC-011`), with the app's defaults. The caller — the app
 * shell's composition root (`TASK-044`) — owns the returned client and closes it.
 */
public fun androidRickAndMortyHttpClient(timeouts: RemoteTimeouts = RemoteTimeouts()): HttpClient =
    HttpClient(OkHttp) {
        engine { preconfigured = apiOkHttpClient() }
        rickAndMortyDefaults(timeouts)
    }

/**
 * The OkHttp client under the engine. It has no disk cache: the app owns freshness, and the
 * server's 90-day `immutable` directive would otherwise pin a JSON page (`API-CACHE-003`). It never
 * follows a redirect by itself, so a redirect cannot reach another host behind Ktor's back.
 */
internal fun apiOkHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

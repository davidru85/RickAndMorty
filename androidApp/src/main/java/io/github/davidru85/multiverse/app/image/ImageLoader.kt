package io.github.davidru85.multiverse.app.image

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.github.davidru85.multiverse.core.designsystem.components.PortraitCrossfadeMillis
import okio.Path.Companion.toOkioPath
import io.ktor.client.HttpClient

/** The disk-cache budget `API_SPECS.md` §14 / `UI_SPEC.md` §5.2 record for the image cache. */
public const val IMAGE_DISK_CACHE_BYTES: Long = 64L * 1024 * 1024

/** The memory-cache fraction of the process's available memory (`UI_SPEC.md` §5.2). */
public const val IMAGE_MEMORY_CACHE_FRACTION: Double = 0.25

/**
 * The app's one image loader (`TASK-021`, `DEC-097`, `DEC-026`).
 *
 * It fetches through the **same allow-listed Ktor client** the data path uses, so an image URL that
 * fails the host rule issues zero transport calls (`SECURITY.md` §5); it keeps Coil's memory cache
 * and a bounded disk cache, so a second render of one URL performs no network request
 * (`AC-REQ-FUNC-021-1`). Image bytes never enter the JSON path: they are Coil's, keyed by the URL
 * (`AC-REQ-FUNC-021-2`).
 *
 * The loader is built by the composition root and owned for the process lifetime.
 */
public fun imageLoader(
    context: Context,
    client: HttpClient,
    diskCacheDirectory: java.io.File,
    memoryCacheFraction: Double = IMAGE_MEMORY_CACHE_FRACTION,
    diskCacheBytes: Long = IMAGE_DISK_CACHE_BYTES,
): ImageLoader =
    ImageLoader
        .Builder(context as PlatformContext)
        .components {
            add(KtorNetworkFetcherFactory(httpClient = { client }))
        }.memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, memoryCacheFraction)
                .build()
        }.diskCache {
            DiskCache.Builder()
                .directory(diskCacheDirectory.toOkioPath())
                .maxSizeBytes(diskCacheBytes)
                .build()
        }.crossfade(PortraitCrossfadeMillis)
        .build()

/** The directory the image disk cache uses, inside the app's private storage (`SECURITY.md` §3). */
public fun imageCacheDirectory(context: Context): java.io.File = java.io.File(context.cacheDir, "images")

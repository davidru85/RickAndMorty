package io.github.davidru85.multiverse.core.data.cache

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * The iOS response-cache store (`IC-012`, `TASK-058`).
 *
 * The exact peer of the Android `FileCacheStorage`: one file per key under the app's private
 * **caches** directory, the same on-disk format through the shared [encodeCacheEntry] /
 * [decodeCacheEntry], the same byte bound with least-recently-modified eviction, and the same
 * degrade-to-a-miss behaviour — a read that fails for any reason is a miss without an exception, so a
 * corrupt or unreadable file costs a network request rather than a crash (`IC-012`, `REQ-FUNC-020`).
 *
 * The **caches** directory is the right home: the OS may evict it at any time, and a missing entry
 * degrades to the offline error state — acceptable here, unlike for favourites (`ADR-0005`).
 *
 * It never reads the wall clock — the timestamp travels inside [CacheEntry] — so only the cache above
 * it decides freshness from the injected clock (`REQ-REL-004`).
 */
@OptIn(ExperimentalForeignApi::class)
public class NsFileCacheStorage(
    private val directory: String,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : CacheStorage {
    private val fileManager = NSFileManager.defaultManager

    override suspend fun get(key: CacheKey): CacheEntry? =
        runCatching {
            val data = NSData.dataWithContentsOfFile(entryPath(key)) ?: return null
            decodeCacheEntry(data.toByteArray())
        }.getOrNull()

    override suspend fun put(
        key: CacheKey,
        entry: CacheEntry,
    ) {
        runCatching {
            fileManager.createDirectoryAtPath(
                path = directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            // `atomically = true` is the platform's own rename-based write, so a partial write can
            // never leave a truncated entry behind the way a plain write could.
            encodeCacheEntry(entry).toNSData().writeToFile(entryPath(key), atomically = true)
            trim()
        }.onFailure {
            // A store that cannot write costs a cache miss; it never fails a caller (`IC-012`).
        }
    }

    override suspend fun evict(key: CacheKey) {
        runCatching { fileManager.removeItemAtPath(entryPath(key), error = null) }
    }

    override suspend fun evictAll() {
        runCatching { entryNames().forEach { fileManager.removeItemAtPath("$directory/$it", error = null) } }
    }

    /** Removes the oldest entries until the store fits [maxBytes]. */
    private fun trim() {
        val entries = entryNames().mapNotNull { name -> attributesOf("$directory/$name")?.let { name to it } }
        var total = entries.sumOf { it.second.size }
        if (total <= maxBytes) return
        for ((name, attributes) in entries.sortedBy { it.second.modified }) {
            if (total <= maxBytes) break
            if (fileManager.removeItemAtPath("$directory/$name", error = null)) total -= attributes.size
        }
    }

    private fun entryNames(): List<String> =
        fileManager
            .contentsOfDirectoryAtPath(directory, error = null)
            ?.filterIsInstance<String>()
            ?.filter { it.endsWith(CACHE_ENTRY_SUFFIX) }
            .orEmpty()

    private data class SizeAndModified(
        val size: Long,
        val modified: Double,
    )

    private fun attributesOf(path: String): SizeAndModified? {
        val attributes = fileManager.attributesOfItemAtPath(path, error = null) ?: return null
        val size = (attributes[NSFileSize] as? NSNumber)?.integerValue?.toLong() ?: 0L
        val modified = (attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSinceReferenceDate ?: 0.0
        return SizeAndModified(size, modified)
    }

    private fun entryPath(key: CacheKey): String = "$directory/${cacheFileName(key.value)}"

    /** The bytes [data] carries, copied into a Kotlin array the codec can read. */
    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).apply {
            usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
        }
    }

    /** [this] as an `NSData`, copied so the Kotlin array may be collected afterwards. */
    @OptIn(ExperimentalForeignApi::class)
    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) {
            NSData()
        } else {
            usePinned { pinned -> NSData.dataWithBytes(bytes = pinned.addressOf(0), length = size.toULong()) }
        }

    private companion object {
        const val DEFAULT_MAX_BYTES: Long = 20L * 1024 * 1024
    }
}

/** The app's private caches directory plus this app's own folder (`ADR-0005`). */
@OptIn(ExperimentalForeignApi::class)
public fun iosResponseCacheDirectory(): String {
    val urls =
        NSFileManager.defaultManager.URLsForDirectory(
            directory = NSCachesDirectory,
            inDomains = NSUserDomainMask,
        )
    val base = (urls.firstOrNull() as? NSURL)?.path ?: "."
    return "$base/multiverse-response-cache"
}

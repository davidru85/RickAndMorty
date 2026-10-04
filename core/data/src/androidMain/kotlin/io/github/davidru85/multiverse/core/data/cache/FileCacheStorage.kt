package io.github.davidru85.multiverse.core.data.cache

import java.io.File
import java.io.IOException

/**
 * The Android response-cache store (`IC-012`, `TASK-020`).
 *
 * One file per key under the app's private **cache** directory. That directory is the right home: the
 * OS may evict it at any time, and a missing entry degrades to the offline error state — acceptable
 * here, unlike for favourites (`adr/0005-caching-strategy.md`, `DEC-004`).
 *
 * The store owns bytes and nothing else. It never reads the wall clock — the timestamp travels inside
 * [CacheEntry] — and a read that fails for any reason is a miss without an exception, so a corrupt or
 * unreadable file costs a network request rather than a crash (`IC-012`, `REQ-FUNC-020`). A write that
 * fails is contained the same way.
 *
 * The bound is a plain byte count over the directory: a write that would exceed [maxBytes] removes the
 * least recently modified entries first. Nothing here expresses freshness; that policy belongs to the
 * cache above, which is the only thing that reads the injected clock.
 */
public class FileCacheStorage(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : CacheStorage {
    override suspend fun get(key: CacheKey): CacheEntry? =
        try {
            val file = entryFile(key)
            if (!file.isFile) null else decodeCacheEntry(file.readBytes())
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    override suspend fun put(
        key: CacheKey,
        entry: CacheEntry,
    ) {
        try {
            directory.mkdirs()
            val file = entryFile(key)
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeBytes(encodeCacheEntry(entry))
            // A rename is atomic where a partial write would leave a truncated entry behind.
            if (!temporary.renameTo(file)) {
                file.writeBytes(temporary.readBytes())
                temporary.delete()
            }
            trim()
        } catch (_: IOException) {
            // A store that cannot write costs a cache miss; it never fails a caller (IC-012).
        }
    }

    override suspend fun evict(key: CacheKey) {
        runCatching { entryFile(key).delete() }
    }

    override suspend fun evictAll() {
        runCatching { directory.listFiles()?.forEach { it.delete() } }
    }

    /** Removes the oldest entries until the store fits [maxBytes]. */
    private fun trim() {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(CACHE_ENTRY_SUFFIX) } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun entryFile(key: CacheKey): File = File(directory, cacheFileName(key.value))

    private companion object {
        /** The store's bound, the order of magnitude `adr/0005-caching-strategy.md` records. */
        const val DEFAULT_MAX_BYTES: Long = 20L * 1024 * 1024
    }
}

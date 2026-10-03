package io.github.davidru85.multiverse.core.data.cache

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.time.Instant

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
            if (!file.isFile) null else decode(file.readBytes())
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
            temporary.writeBytes(encode(entry))
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
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun entryFile(key: CacheKey): File = File(directory, "${key.value.sha256Hex()}$SUFFIX")

    /**
     * The key is hashed, not escaped: a normalized request identity contains `/`, `|`, `&` and `%`, and
     * a path built from it would nest directories or escape the store's root.
     */
    private fun String.sha256Hex(): String =
        MessageDigest
            .getInstance(SHA_256)
            .digest(encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val SHA_256 = "SHA-256"
        const val SUFFIX = ".entry"

        /** The store's bound, the order of magnitude `adr/0005-caching-strategy.md` records. */
        const val DEFAULT_MAX_BYTES: Long = 20L * 1024 * 1024
    }
}

/**
 * The stored form of one entry: the payload plus the two facts a later read needs. It is versioned by
 * being discarded rather than migrated, so a format change degrades to a cold cache.
 */
private fun encode(entry: CacheEntry): ByteArray {
    val payload = entry.payload
    val validator = entry.validator?.encodeToByteArray()
    val buffer = ByteArray(HEADER + payload.size + (validator?.size ?: 0))
    var offset = 0

    fun putLong(value: Long) {
        for (shift in 56 downTo 0 step 8) {
            buffer[offset++] = (value ushr shift).toByte()
        }
    }
    putLong(MAGIC)
    putLong(entry.storedAt.toEpochMilliseconds())
    putLong((validator?.size ?: NO_VALIDATOR).toLong())
    payload.copyInto(buffer, offset)
    offset += payload.size
    validator?.copyInto(buffer, offset)
    return buffer
}

@Suppress("ReturnCount")
private fun decode(bytes: ByteArray): CacheEntry? {
    if (bytes.size < HEADER) return null
    var offset = 0

    fun readLong(): Long {
        var value = 0L
        repeat(8) { value = (value shl 8) or (bytes[offset++].toLong() and 0xFF) }
        return value
    }
    if (readLong() != MAGIC) return null
    val storedAt = readLong()
    val validatorLength = readLong().toInt()
    val payloadEnd = bytes.size - if (validatorLength < 0) 0 else validatorLength
    if (validatorLength >= 0 && validatorLength > bytes.size - offset) return null
    if (payloadEnd < offset) return null
    val payload = bytes.copyOfRange(offset, payloadEnd)
    val validator =
        if (validatorLength < 0) {
            null
        } else {
            bytes.copyOfRange(payloadEnd, bytes.size).decodeToString()
        }
    return CacheEntry(payload, Instant.fromEpochMilliseconds(storedAt), validator)
}

// `MVRSCACH` as ASCII, so a foreign file is rejected before anything is read from it.
private const val MAGIC: Long = 0x4D_56_52_53_43_41_43_48
private const val HEADER: Int = 8 + 8 + 8
private const val NO_VALIDATOR: Int = -1

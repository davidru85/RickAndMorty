package io.github.davidru85.multiverse.core.data.cache

import kotlin.time.Instant

/**
 * The stored form of one [CacheEntry] (`IC-012`), shared by both platform stores.
 *
 * The codec lives in `commonMain` rather than beside either store because the format is part of the
 * one contract `CacheStorage` defines: `TESTING.md` §6.2 requires an `expect/actual` store to satisfy
 * the same contract suite on both targets, and two copies of a byte format would be a silent second
 * format waiting to diverge. Both stores read and write these bytes.
 *
 * The format is versioned by being **discarded rather than migrated**: an entry that does not decode
 * is a miss, so a format change degrades to a cold cache instead of to a wrong value.
 *
 * Every entry file carries the [CACHE_ENTRY_SUFFIX] extension on both platforms.
 */
internal const val CACHE_ENTRY_SUFFIX: String = ".entry"

/** The magic that identifies an entry this app wrote; a foreign file is a miss. */
private const val CACHE_ENTRY_MAGIC: Long = 0x4D_56_52_53_43_41_43_48 // "MVRSCACH"

/** The header's size: the magic, the stored instant and the validator's length. */
private const val CACHE_ENTRY_HEADER: Int = 8 + 8 + 8

/** The validator-length sentinel for an entry that carries no validator. */
private const val CACHE_ENTRY_NO_VALIDATOR: Int = -1

/** The bytes for [entry]: a magic, the stored instant, the validator's length and the payload. */
internal fun encodeCacheEntry(entry: CacheEntry): ByteArray {
    val payload = entry.payload
    val validator = entry.validator?.encodeToByteArray()
    val buffer = ByteArray(CACHE_ENTRY_HEADER + payload.size + (validator?.size ?: 0))
    var offset = 0

    fun putLong(value: Long) {
        for (shift in 56 downTo 0 step 8) {
            buffer[offset++] = (value ushr shift).toByte()
        }
    }
    putLong(CACHE_ENTRY_MAGIC)
    putLong(entry.storedAt.toEpochMilliseconds())
    putLong((validator?.size ?: CACHE_ENTRY_NO_VALIDATOR).toLong())
    payload.copyInto(buffer, offset)
    offset += payload.size
    validator?.copyInto(buffer, offset)
    return buffer
}

/** The entry [bytes] describe, or `null` when they are truncated, foreign or otherwise unreadable. */
internal fun decodeCacheEntry(bytes: ByteArray): CacheEntry? {
    if (bytes.size < CACHE_ENTRY_HEADER) return null
    var offset = 0

    fun readLong(): Long {
        var value = 0L
        repeat(8) { value = (value shl 8) or (bytes[offset++].toLong() and 0xFF) }
        return value
    }
    if (readLong() != CACHE_ENTRY_MAGIC) return null
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

/**
 * The entry file name for [value]: the key hashed, not escaped.
 *
 * A normalized request identity contains `/`, `|`, `&` and `%`, and a path built from it would nest
 * directories or escape the store's root. Both platform stores derive the name the same way, so the
 * same key is the same file on either platform.
 */
internal fun cacheFileName(value: String): String = "${value.sha256Hex()}$CACHE_ENTRY_SUFFIX"

/** The lowercase hex SHA-256 of this string, implemented in common code so both stores agree. */
internal fun String.sha256Hex(): String {
    val bytes = encodeToByteArray()
    val hash = sha256(bytes)

    // A hand-rolled hex rather than `format`, which is not in the common stdlib: the digest is a
    // `ByteArray` and each byte needs two lowercase hex digits.
    val digits = "0123456789abcdef"
    return buildString(hash.size * 2) {
        for (byte in hash) {
            val value = byte.toInt() and 0xFF
            append(digits[value ushr 4])
            append(digits[value and 0x0F])
        }
    }
}

/**
 * A compact SHA-256 (`FIPS 180-4`).
 *
 * It is written here rather than taken from a dependency because the store needs exactly one hash and
 * `REQ-NFR-002` caps the dependency count; the implementation is the standard one, with the message
 * padded and the 64-round compression applied to a state that starts at the published constants.
 */
@OptIn(ExperimentalUnsignedTypes::class)
private fun sha256(message: ByteArray): ByteArray {
    val k = SHA256_K
    var h0 = 0x6a09e667u
    var h1 = 0xbb67ae85u
    var h2 = 0x3c6ef372u
    var h3 = 0xa54ff53au
    var h4 = 0x510e527fu
    var h5 = 0x9b05688cu
    var h6 = 0x1f83d9abu
    var h7 = 0x5be0cd19u

    val padded = message.copyOf(((message.size + 9 + 63) / 64) * 64)
    padded[message.size] = 0x80.toByte()
    val bitLength = message.size.toLong() * 8
    for (i in 0 until 8) {
        padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
    }

    val w = UIntArray(64)
    var offset = 0
    while (offset < padded.size) {
        for (i in 0 until 16) {
            val base = offset + i * 4
            w[i] = bigEndianWord(padded, base)
        }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] shr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] shr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7
        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + k[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }
        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
        offset += 64
    }

    val out = ByteArray(32)
    listOf(h0, h1, h2, h3, h4, h5, h6, h7).forEachIndexed { index, value ->
        out[index * 4] = (value shr 24).toByte()
        out[index * 4 + 1] = (value shr 16).toByte()
        out[index * 4 + 2] = (value shr 8).toByte()
        out[index * 4 + 3] = value.toByte()
    }
    return out
}

/** The round constants of `FIPS 180-4` §4.2.2. */
@OptIn(ExperimentalUnsignedTypes::class)
private val SHA256_K: UIntArray =
    uintArrayOf(
        0x428a2f98u,
        0x71374491u,
        0xb5c0fbcfu,
        0xe9b5dba5u,
        0x3956c25bu,
        0x59f111f1u,
        0x923f82a4u,
        0xab1c5ed5u,
        0xd807aa98u,
        0x12835b01u,
        0x243185beu,
        0x550c7dc3u,
        0x72be5d74u,
        0x80deb1feu,
        0x9bdc06a7u,
        0xc19bf174u,
        0xe49b69c1u,
        0xefbe4786u,
        0x0fc19dc6u,
        0x240ca1ccu,
        0x2de92c6fu,
        0x4a7484aau,
        0x5cb0a9dcu,
        0x76f988dau,
        0x983e5152u,
        0xa831c66du,
        0xb00327c8u,
        0xbf597fc7u,
        0xc6e00bf3u,
        0xd5a79147u,
        0x06ca6351u,
        0x14292967u,
        0x27b70a85u,
        0x2e1b2138u,
        0x4d2c6dfcu,
        0x53380d13u,
        0x650a7354u,
        0x766a0abbu,
        0x81c2c92eu,
        0x92722c85u,
        0xa2bfe8a1u,
        0xa81a664bu,
        0xc24b8b70u,
        0xc76c51a3u,
        0xd192e819u,
        0xd6990624u,
        0xf40e3585u,
        0x106aa070u,
        0x19a4c116u,
        0x1e376c08u,
        0x2748774cu,
        0x34b0bcb5u,
        0x391c0cb3u,
        0x4ed8aa4au,
        0x5b9cca4fu,
        0x682e6ff3u,
        0x748f82eeu,
        0x78a5636fu,
        0x84c87814u,
        0x8cc70208u,
        0x90befffau,
        0xa4506cebu,
        0xbef9a3f7u,
        0xc67178f2u,
    )

/** The four bytes at [offset] as one big-endian word, which is how SHA-256 reads a message. */
@OptIn(ExperimentalUnsignedTypes::class)
private fun bigEndianWord(
    bytes: ByteArray,
    offset: Int,
): UInt =
    ((bytes[offset].toUInt() and 0xFFu) shl 24) or
        ((bytes[offset + 1].toUInt() and 0xFFu) shl 16) or
        ((bytes[offset + 2].toUInt() and 0xFFu) shl 8) or
        (bytes[offset + 3].toUInt() and 0xFFu)

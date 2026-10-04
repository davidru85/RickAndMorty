package io.github.davidru85.multiverse.core.data.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The shared cache-entry codec and the file-name hash (`IC-012`, `TASK-058`).
 *
 * The codec moved into `commonMain` so both platform stores read and write one format; that is only
 * safe if the format and the hash are pinned. The SHA-256 cases are the **published** `FIPS 180-4`
 * vectors, so a change to the hand-written hash fails here rather than silently renaming every cache
 * file on upgrade (a rename would be a silent cold cache) or colliding two keys onto one file.
 *
 * The round-trip case covers the validator's absence and presence, which are the two shapes the store
 * writes, and a foreign or truncated file is a miss rather than an exception.
 */
class CacheEntryCodecTest {
    @Test
    fun `given_the_published_sha256_vectors_when_hashed_then_the_digest_matches`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "".sha256Hex())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "abc".sha256Hex())
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".sha256Hex(),
        )
    }

    @Test
    fun `given_a_key_when_a_file_name_is_derived_then_it_is_the_hashed_key_and_never_the_key`() {
        val name = cacheFileName("character|list|page=1|status=alive")
        assertEquals(64 + ".entry".length, name.length)
        assertEquals(true, name.endsWith(".entry"))
        // A path built from the key itself would nest directories or escape the store root.
        assertEquals(false, name.contains("/"))
        assertEquals(false, name.contains("&"))
    }

    @Test
    fun `given_two_distinct_keys_when_named_then_the_names_differ`() {
        assertEquals(false, cacheFileName("a") == cacheFileName("b"))
    }

    @Test
    fun `given_an_entry_with_a_validator_when_encoded_and_decoded_then_every_field_survives`() {
        val entry = CacheEntry("payload".encodeToByteArray(), Instant.fromEpochMilliseconds(1_234_567), "\"etag\"")
        val restored = decodeCacheEntry(encodeCacheEntry(entry))
        assertEquals(entry.payload.decodeToString(), restored?.payload?.decodeToString())
        assertEquals(entry.storedAt, restored?.storedAt)
        assertEquals(entry.validator, restored?.validator)
    }

    @Test
    fun `given_an_entry_without_a_validator_when_encoded_and_decoded_then_the_absence_survives`() {
        val entry = CacheEntry("{}".encodeToByteArray(), Instant.fromEpochMilliseconds(42), null)
        val restored = decodeCacheEntry(encodeCacheEntry(entry))
        assertEquals("{}", restored?.payload?.decodeToString())
        assertNull(restored?.validator, "an entry stored without a validator must not gain an empty one")
    }

    @Test
    fun `given_a_foreign_or_truncated_file_when_decoded_then_it_is_a_miss_and_never_an_exception`() {
        assertNull(decodeCacheEntry(ByteArray(0)))
        assertNull(decodeCacheEntry("not our format at all, but long enough".encodeToByteArray()))
        assertNull(decodeCacheEntry(encodeCacheEntry(CacheEntry(ByteArray(4), Instant.fromEpochMilliseconds(1), null)).copyOf(10)))
    }
}

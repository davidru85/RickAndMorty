package io.github.davidru85.multiverse.core.data.cache

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * `TEST-INT-001`'s store half and `TEST-INT-004` — the Android response-cache store.
 *
 * The cases drive the real [FileCacheStorage] over a unique temporary directory, so the file format,
 * the atomic write, the byte budget and the "a broken file is a miss" rule are asserted against the
 * implementation that ships rather than against a double (`IC-012`, `TESTING.md` §6, §6.2). No case
 * opens a socket or reads the wall clock.
 */
class FileCacheStorageTest {
    private val root: File = Files.createTempDirectory("multiverse-cache-test").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun key(value: String) = CacheKey(value)

    @Test
    fun `TEST-INT-001 given_a_stored_entry_when_it_is_read_back_then_its_payload_timestamp_and_validator_survive`() =
        runTest {
            val storage = FileCacheStorage(root)
            val entry = CacheEntry("payload".encodeToByteArray(), Instant.fromEpochMilliseconds(1_760_000_000_000), "W/\"etag\"")

            storage.put(key("rest|GET|/api/character|page=1"), entry)

            val read = assertNotNull(storage.get(key("rest|GET|/api/character|page=1")))
            assertContentEquals(entry.payload, read.payload, "the bytes come back unchanged")
            assertEquals(entry.storedAt, read.storedAt, "the stored instant is the one the clock gave, not the read time")
            assertEquals(entry.validator, read.validator)
        }

    @Test
    fun `TEST-INT-001 given_no_entry_when_the_store_is_read_then_it_is_a_miss`() =
        runTest {
            assertNull(FileCacheStorage(root).get(key("rest|GET|/api/character|page=1")))
        }

    @Test
    fun `TEST-INT-001 given_a_corrupt_file_when_it_is_read_then_it_is_a_miss_and_no_exception_escapes`() =
        runTest {
            val storage = FileCacheStorage(root)
            storage.put(key("rest|GET|/api/character|page=1"), CacheEntry(byteArrayOf(1, 2, 3), Instant.fromEpochMilliseconds(0)))
            // A truncated or foreign file is exactly what an interrupted write and a format change leave behind.
            root.listFiles()!!.single().writeBytes("not the format we wrote".encodeToByteArray())

            assertNull(storage.get(key("rest|GET|/api/character|page=1")), "a corrupt entry is a miss")
        }

    @Test
    fun `TEST-INT-001 given_an_entry_when_it_is_evicted_then_only_it_disappears`() =
        runTest {
            val storage = FileCacheStorage(root)
            storage.put(key("a"), CacheEntry("a".encodeToByteArray(), Instant.fromEpochMilliseconds(0)))
            storage.put(key("b"), CacheEntry("b".encodeToByteArray(), Instant.fromEpochMilliseconds(0)))

            storage.evict(key("a"))

            assertNull(storage.get(key("a")))
            assertNotNull(storage.get(key("b")), "the other entry is untouched")
        }

    @Test
    fun `TEST-INT-001 given_a_full_store_when_another_entry_is_written_then_the_budget_is_respected`() =
        runTest {
            val storage = FileCacheStorage(root, maxBytes = 2_000)
            repeat(20) { index ->
                storage.put(
                    key("rest|GET|/api/character|page=$index"),
                    CacheEntry(ByteArray(200), Instant.fromEpochMilliseconds(index.toLong())),
                )
            }

            val total = root.listFiles()!!.sumOf { it.length() }
            assertTrue(total <= 2_000, "the store stays inside its byte budget (was $total)")
        }

    @Test
    fun `TEST-INT-001 given_the_store_when_all_entries_are_evicted_then_it_is_empty`() =
        runTest {
            val storage = FileCacheStorage(root)
            storage.put(key("a"), CacheEntry("a".encodeToByteArray(), Instant.fromEpochMilliseconds(0)))

            storage.evictAll()

            assertNull(storage.get(key("a")))
            assertEquals(0, root.listFiles()?.count { it.name.endsWith(".entry") } ?: 0)
        }

    @Test
    fun `TEST-INT-001 given_a_key_that_looks_like_a_path_when_it_is_stored_then_it_cannot_escape_the_directory`() =
        runTest {
            val storage = FileCacheStorage(root)
            val hostile = key("rest|GET|../../../../etc/passwd|page=1")

            storage.put(hostile, CacheEntry("payload".encodeToByteArray(), Instant.fromEpochMilliseconds(0)))

            assertNotNull(storage.get(hostile))
            assertEquals(1, root.listFiles()!!.size, "the key is hashed into one file inside the store's own directory")
        }
}

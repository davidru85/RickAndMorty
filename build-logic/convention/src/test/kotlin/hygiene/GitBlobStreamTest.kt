package io.github.davidru85.multiverse.buildlogic.hygiene

import java.io.File
import kotlin.test.assertEquals
import org.junit.Test

/** TEST-UNIT-026: real batch traffic must not deadlock when both OS pipes fill. */
class GitBlobStreamTest {
    @Test(timeout = 15_000)
    fun `requests larger than a pipe buffer are completely scanned`() {
        val root = kotlin.io.path.createTempDirectory("git-blob-stream").toFile()
        fun git(vararg arguments: String): String {
            val process = ProcessBuilder(listOf("git") + arguments).directory(root).start()
            val text = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor())
            return text.trim()
        }
        git("init", "--quiet")
        File(root, "fixture.txt").writeText("blob fixture\n".repeat(64))
        val id = git("hash-object", "-w", "fixture.txt")
        // Repeating an object stresses the transport without creating thousands of files.
        val count = 32_768
        var starts = 0
        var ends = 0
        var bytes = 0
        assertEquals(
            count,
            GitRepository.open(root).forEachBlob(
                List(count) { id },
                { starts++ },
                { bytes += it.length },
                { ends++ },
            ),
        )
        assertEquals(count, starts)
        assertEquals(count, ends)
        assertEquals(count * File(root, "fixture.txt").length(), bytes.toLong())
        root.deleteRecursively()
    }
}

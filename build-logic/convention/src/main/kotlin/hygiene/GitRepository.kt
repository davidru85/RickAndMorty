package io.github.davidru85.multiverse.buildlogic.hygiene

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The Git-backed view of the repository that `verifyRepositoryHygiene` scans
 * (`TEST-UNIT-026`, `REQ-SEC-002`, `AC-REQ-SEC-002-1`; DEC-062).
 *
 * Every Git command is run with an argument array — never an interpolated shell string —
 * and every result is read as bytes, so path names containing spaces, tabs, newlines or
 * non-ASCII characters survive unchanged. Git is invoked through [ProcessBuilder] rather
 * than Gradle's `Exec` support so that configuration cache never has to carry a captured
 * process result, and so that a large blob stream can be read without materialising it as
 * one string.
 *
 * Every parser here fails **closed**: an unexpected output shape, an undecodable path, a
 * non-zero exit with empty stderr, a truncated or over-long batch stream, or a response
 * that does not match its request raises [GitFailure] rather than being read as an empty
 * or partial result. A clean run therefore never means "the command produced nothing".
 *
 * The identity contract is the Git top level, not the working directory: when Git reports
 * a top level different from the Gradle root, the repository is not the one being built
 * and the caller must fail (HYG-05).
 */
internal class GitRepository private constructor(private val root: File) {

    /** A Git failure that must fail the task closed rather than be read as zero findings. */
    class GitFailure(message: String) : IOException(message)

    /** A symlink that would have to be followed out of the repository (HYG-07). */
    class ExternalSymlink(val relativePath: String) : IOException()

    companion object {

        private const val ZERO_ID = "0000000000000000000000000000000000000000"
        private const val GITLINK_MODE = "160000"
        private const val MAX_BLOB_BYTES = 256L * 1024 * 1024
        private val OBJECT_TYPES = setOf("blob", "tree", "commit", "tag")

        fun open(projectRoot: File): GitRepository {
            val workTree = canonical(projectRoot)
            val topLevel = run(
                root = workTree,
                command = listOf("git", "rev-parse", "--show-toplevel"),
                errorMessage = "the Gradle root is not inside a Git work tree",
            ).text().trim()
            if (topLevel.isEmpty()) throw GitFailure("Git reported an empty work-tree root.")
            val root = canonical(File(topLevel))
            if (root != workTree) {
                throw GitFailure(
                    "the Git work-tree root is `$root` but the Gradle root is `$workTree`; " +
                        "the hygiene check refuses to scan a different repository (HYG-05)",
                )
            }
            run(root, listOf("git", "rev-parse", "--absolute-git-dir"), "the repository has no Git directory")
            return GitRepository(root)
        }

        /** Canonicalizes a path with the real filesystem case, failing closed on an error. */
        fun canonical(file: File): File = try {
            file.canonicalFile
        } catch (e: IOException) {
            throw GitFailure("cannot canonicalize `$file`: ${e.message}")
        }

        private fun run(root: File, command: List<String>, errorMessage: String): ProcessResult {
            val process = try {
                ProcessBuilder(command).directory(root).redirectErrorStream(false).start()
            } catch (e: IOException) {
                throw GitFailure("$errorMessage: Git could not be started (${e.message})")
            }
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val outThread = Thread { drain(process.inputStream, stdout) }
            val errThread = Thread { drain(process.errorStream, stderr) }
            outThread.start()
            errThread.start()
            val status = process.waitFor()
            outThread.join()
            errThread.join()
            if (status != 0) {
                val detail = String(stderr.toByteArray(), Charsets.UTF_8).trim()
                throw GitFailure("$errorMessage: git exited with status $status${if (detail.isEmpty()) "" else ": $detail"}")
            }
            return ProcessResult(stdout.toByteArray())
        }

        private fun drain(input: InputStream, sink: ByteArrayOutputStream) {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return
                sink.write(buffer, 0, read)
            }
        }

        /** NUL-separated records, lossless for every legal path name. */
        private fun splitNul(bytes: ByteArray): List<ByteArray> {
            val records = mutableListOf<ByteArray>()
            var start = 0
            for (index in bytes.indices) {
                if (bytes[index] == 0.toByte()) {
                    if (index > start) records += bytes.copyOfRange(start, index)
                    start = index + 1
                }
            }
            if (start < bytes.size) records += bytes.copyOfRange(start, bytes.size)
            return records
        }

        /** Strict UTF-8: a byte sequence Git could not have produced as a path fails closed. */
        private fun decode(bytes: ByteArray, what: String): String = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw GitFailure("$what is not a decodable text sequence; refusing to guess")
        }
    }

    /** The raw bytes a Git command produced, decoded leniently for reporting only. */
    class ProcessResult(internal val bytes: ByteArray) {
        fun text(): String = String(bytes, Charsets.UTF_8)
    }

    /**
     * `git rev-parse --is-shallow-repository` (HYG-06). A shallow checkout hides history, so
     * a clean result there would be a false guarantee. Any answer that is not exactly `true`
     * or `false` is a failure, never silently read as "not shallow".
     */
    fun isShallow(): Boolean = when (
        val value = git("rev-parse", "--is-shallow-repository", errorMessage = "cannot read the checkout depth").text().trim()
    ) {
        "true" -> true
        "false" -> false
        else -> throw GitFailure("Git returned an unexpected answer for the checkout depth; the scan cannot proceed (HYG-06)")
    }

    /**
     * The commit-eligible working set: tracked files plus untracked, non-ignored files, with
     * duplicates removed. Ignored caches and build output are deliberately outside it; their
     * path classes are held by HYG-01/HYG-02/HYG-03.
     */
    fun workingSet(): List<String> {
        val bytes = git(
            "ls-files",
            "-z",
            "--cached",
            "--others",
            "--exclude-standard",
            errorMessage = "cannot enumerate the commit-eligible working set",
        ).bytes
        val paths = sortedSetOf<String>()
        splitNul(bytes).forEach { paths += decode(it, "a commit-eligible path") }
        return paths.toList()
    }

    /** The index mode per tracked path, so a symlink or gitlink is detected before it is read. */
    fun indexModes(): Map<String, String> {
        val bytes = git("ls-files", "-s", "-z", errorMessage = "cannot read the index").bytes
        val modes = linkedMapOf<String, String>()
        splitNul(bytes).forEach { record ->
            val text = decode(record, "an index entry")
            val tab = text.indexOf('\t')
            if (tab <= 0) throw GitFailure("the index contains a record without a path separator; refusing to skip it")
            val header = text.substring(0, tab).trim().split(' ').filter { it.isNotEmpty() }
            if (header.size != 3) throw GitFailure("the index contains a malformed record; refusing to skip it")
            val mode = header[0]
            if (mode.length != 6 || !mode.all { it in "01234567" }) {
                throw GitFailure("the index contains an entry with an unreadable mode; refusing to skip it")
            }
            modes[text.substring(tab + 1)] = mode
        }
        return modes
    }

    /** `git ls-files -ci --exclude-standard`: tracked paths that a rule now ignores (HYG-02). */
    fun trackedIgnoredPaths(): List<String> {
        val bytes = git(
            "ls-files",
            "-ci",
            "--exclude-standard",
            "-z",
            errorMessage = "cannot list tracked ignored paths",
        ).bytes
        return splitNul(bytes).map { decode(it, "a tracked ignored path") }.sorted()
    }

    /**
     * Resolves candidate paths against the ignore rules exactly as Git does, tracked or not,
     * and reports the deciding rule. `check-ignore` exits 0 when at least one path is ignored,
     * 1 when none is, and 128 on a real error; only the last is a failure. A record set whose
     * size is not a multiple of the four `-z -v` fields is a failure, never a partial read.
     */
    fun ignoreStatus(candidates: List<String>): Map<String, IgnoreMatch> {
        if (candidates.isEmpty()) return emptyMap()
        val input = candidates.joinToString(separator = "") { "$it\u0000" }
        val process = try {
            ProcessBuilder(listOf("git", "check-ignore", "--no-index", "-v", "-z", "--stdin"))
                .directory(root)
                .redirectErrorStream(false)
                .start()
        } catch (e: IOException) {
            throw GitFailure("cannot evaluate the ignore rules: Git could not be started (${e.message})")
        }
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val outThread = Thread { drain(process.inputStream, stdout) }
        val errThread = Thread { drain(process.errorStream, stderr) }
        outThread.start()
        errThread.start()
        process.outputStream.use { it.write(input.toByteArray(Charsets.UTF_8)) }
        val status = process.waitFor()
        outThread.join()
        errThread.join()
        if (status != 0 && status != 1) {
            val detail = String(stderr.toByteArray(), Charsets.UTF_8).trim()
            throw GitFailure("cannot evaluate the ignore rules: git exited with status $status: $detail")
        }
        val records = splitNul(stdout.toByteArray())
        if (records.size % 4 != 0) {
            throw GitFailure("the ignore-rule output has an unexpected shape; refusing to read it partially")
        }
        val matches = linkedMapOf<String, IgnoreMatch>()
        var index = 0
        while (index < records.size) {
            val line = decode(records[index + 1], "an ignore-rule line number").trim().toIntOrNull()
                ?: throw GitFailure("the ignore-rule output has a non-numeric line number; refusing to read it partially")
            matches[decode(records[index + 3], "an ignore-rule path")] = IgnoreMatch(
                source = decode(records[index], "an ignore-rule source"),
                line = line,
                pattern = decode(records[index + 2], "an ignore-rule pattern"),
            )
            index += 4
        }
        return matches
    }

    /** One `.gitignore` rule that decided a path's fate. */
    data class IgnoreMatch(val source: String, val line: Int, val pattern: String)

    /**
     * A commit-eligible file's bytes. Symlinks are never followed: a link whose target
     * resolves outside the repository is an [ExternalSymlink]; a link inside it yields its
     * target text, which is the repository data.
     */
    fun readCandidate(relativePath: String): ByteArray {
        val file = File(root, relativePath)
        if (Files.isSymbolicLink(file.toPath())) {
            val linkText = try {
                Files.readSymbolicLink(file.toPath()).toString()
            } catch (e: IOException) {
                throw GitFailure("cannot read the symlink `$relativePath`: ${e.message}")
            }
            val link = Paths.get(linkText)
            val target = (if (link.isAbsolute) link else file.parentFile.toPath().resolve(link))
                .normalize()
                .toAbsolutePath()
            if (!target.startsWith(root.toPath())) throw ExternalSymlink(relativePath)
            return linkText.toByteArray(Charsets.UTF_8)
        }
        if (!file.isFile) {
            throw GitFailure("`$relativePath` is commit-eligible but is not a regular file")
        }
        return try {
            file.readBytes()
        } catch (e: IOException) {
            throw GitFailure("cannot read `$relativePath`: ${e.message}")
        }
    }

    /**
     * Every object id reachable from every local ref, distinct and sorted. A direct ref to a
     * blob makes that blob reachable with no tree path, so this list is the complete
     * candidate set for the content scan; classification by [checkObjects] then decides which
     * of them are blobs (F-01).
     */
    fun reachableObjectIds(): List<String> {
        val bytes = git(
            "rev-list",
            "--objects",
            "--all",
            "-z",
            errorMessage = "cannot enumerate the objects reachable from local refs",
        ).bytes
        val ids = sortedSetOf<String>()
        splitNul(bytes).forEach { record ->
            val text = decode(record, "a reachable object record")
            if (!text.startsWith("path=")) ids += text
        }
        return ids.toList()
    }

    /**
     * Blob types and sizes for every requested object id, in one `cat-file --batch-check`
     * process. The response count and order must match the request exactly; a missing,
     * extra, misordered or unparseable line is a failure, never a silent skip (F-03).
     */
    fun checkObjects(ids: List<String>): Map<String, ObjectInfo> {
        if (ids.isEmpty()) return emptyMap()
        val input = ids.joinToString(separator = "\n", postfix = "\n")
        val output = runWithInput(
            command = listOf("git", "cat-file", "--batch-check=%(objectname) %(objecttype) %(objectsize)"),
            input = input.toByteArray(Charsets.UTF_8),
            errorMessage = "cannot classify the reachable objects",
        ).bytes
        val lines = String(output, Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size != ids.size) {
            throw GitFailure(
                "Git classified ${lines.size} of ${ids.size} requested object(s); the history scan is " +
                    "incomplete (HYG-06)",
            )
        }
        val infos = linkedMapOf<String, ObjectInfo>()
        lines.forEachIndexed { position, raw ->
            val parts = raw.trim().split(' ').filter { it.isNotEmpty() }
            if (parts.size != 3) throw GitFailure("Git returned an unreadable object classification; the history scan is incomplete")
            val id = parts[0]
            if (id != ids[position]) throw GitFailure("the object classification does not match the request; the history scan is incomplete")
            val type = parts[1]
            if (type !in OBJECT_TYPES) throw GitFailure("Git returned an unknown object type during classification")
            val size = parts[2].toLongOrNull() ?: throw GitFailure("Git returned a non-numeric object size during classification")
            if (size < 0) throw GitFailure("Git returned a negative object size during classification")
            infos[id] = ObjectInfo(type, size)
        }
        return infos
    }

    /**
     * Every historical path occurrence reachable from every local ref, with the paths that
     * ever carried a gitlink (submodule) entry.
     *
     * `rev-list --objects` names an object once, so its single optional name is not an
     * exhaustive path history: the same blob can appear at a safe path and a forbidden
     * carrier path while being named only by the safe one. This method therefore reads a
     * **NUL-safe raw history stream** over `--root -m --no-renames`, which yields one record
     * per introduced, modified or deleted path per commit — including root and merge commits
     * — with the full 40-hex modes so a gitlink is visible (F-02). Rename detection is
     * disabled, so a rename is a delete plus an add and both paths appear.
     */
    fun historyScan(): HistoryScan {
        val bytes = git(
            "log",
            "--all",
            "--root",
            "--raw",
            "--no-renames",
            "-m",
            "-z",
            "--no-abbrev",
            "--pretty=format:%x00",
            errorMessage = "cannot enumerate the historical paths",
        ).bytes
        val records = splitNul(bytes)
        val paths = sortedSetOf<String>()
        val gitlinkPaths = sortedSetOf<String>()
        val blobPathHint = linkedMapOf<String, String>()
        var index = 0
        while (index < records.size) {
            val header = decode(records[index], "a history-record header").trimStart('\n', '\r')
            if (!header.startsWith(":")) {
                index++
                continue
            }
            val fields = header.substring(1).split(' ')
            if (fields.size != 5) throw GitFailure("a history record has an unreadable header; refusing to read it partially")
            val status = fields[4]
            if (status.startsWith("R") || status.startsWith("C")) {
                throw GitFailure("a rename or copy record appeared although rename detection is disabled")
            }
            if (index + 1 >= records.size) throw GitFailure("a history record has no path; refusing to read it partially")
            val path = decode(records[index + 1], "a historical path")
            index += 2
            paths += path
            if (fields[0] == GITLINK_MODE || fields[1] == GITLINK_MODE) gitlinkPaths += path
            val destination = fields[3]
            if (destination != ZERO_ID && destination.length == 40) blobPathHint.putIfAbsent(destination, path)
        }
        return HistoryScan(paths = paths, gitlinkPaths = gitlinkPaths, blobPathHint = blobPathHint)
    }

    /**
     * Streams every requested blob exactly once and hands its validated bytes to [consume],
     * then returns the number of records actually consumed. Each response must match its
     * request by id, be a `blob`, carry a non-negative numeric size and be followed by the
     * record separator; the process must exit zero. A short, long, mismatched or truncated
     * stream raises [GitFailure] instead of reporting a partial scan (F-03).
     */
    fun forEachBlob(ids: List<String>, consume: (String, ByteArray) -> Unit): Int {
        if (ids.isEmpty()) return 0
        val process = try {
            ProcessBuilder(listOf("git", "cat-file", "--batch")).directory(root).redirectErrorStream(false).start()
        } catch (e: IOException) {
            throw GitFailure("cannot read reachable blobs: Git could not be started (${e.message})")
        }
        val stderr = ByteArrayOutputStream()
        val errThread = Thread { drain(process.errorStream, stderr) }
        errThread.start()
        var completed = false
        var consumed = 0
        try {
            process.outputStream.use { it.write(ids.joinToString(separator = "\n", postfix = "\n").toByteArray(Charsets.UTF_8)) }
            val source = process.inputStream
            for (expected in ids) {
                val header = readHeaderLine(source)
                    ?: throw GitFailure("`git cat-file --batch` ended after $consumed of ${ids.size} blob(s); the blob scan is incomplete")
                val parts = header.trim().split(' ').filter { it.isNotEmpty() }
                if (parts.size != 3) throw GitFailure("`git cat-file --batch` returned an unreadable header; the blob scan is incomplete")
                if (parts[0] != expected) throw GitFailure("`git cat-file --batch` returned a record for ${parts[0].take(12)}… while ${expected.take(12)}… was requested")
                if (parts[1] != "blob") throw GitFailure("`git cat-file --batch` returned type `${parts[1]}` where `blob` was requested")
                val size = parts[2].toLongOrNull() ?: throw GitFailure("`git cat-file --batch` returned a non-numeric blob size")
                if (size < 0) throw GitFailure("`git cat-file --batch` returned a negative blob size")
                consume(expected, readBlobBytes(source, size))
                consumed++
            }
            if (source.read() >= 0) throw GitFailure("`git cat-file --batch` returned more records than were requested")
            completed = true
        } finally {
            if (!completed) process.destroyForcibly()
            val status = process.waitFor()
            errThread.join()
            val detail = String(stderr.toByteArray(), Charsets.UTF_8).trim()
            if (completed) {
                if (status != 0) {
                    throw GitFailure(
                        "`git cat-file --batch` exited with status $status" +
                            (if (detail.isEmpty()) " and no diagnostic" else ": $detail") +
                            "; the blob scan is incomplete (HYG-06)",
                    )
                }
                if (detail.isNotEmpty()) throw GitFailure("`git cat-file --batch` reported: $detail")
            }
        }
        return consumed
    }

    private fun runWithInput(command: List<String>, input: ByteArray, errorMessage: String): ProcessResult {
        val process = try {
            ProcessBuilder(command).directory(root).redirectErrorStream(false).start()
        } catch (e: IOException) {
            throw GitFailure("$errorMessage: Git could not be started (${e.message})")
        }
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val outThread = Thread { drain(process.inputStream, stdout) }
        val errThread = Thread { drain(process.errorStream, stderr) }
        outThread.start()
        errThread.start()
        process.outputStream.use { it.write(input) }
        val status = process.waitFor()
        outThread.join()
        errThread.join()
        if (status != 0) {
            val detail = String(stderr.toByteArray(), Charsets.UTF_8).trim()
            throw GitFailure("$errorMessage: git exited with status $status: $detail")
        }
        return ProcessResult(stdout.toByteArray())
    }

    /** A complete batch header line, or `null` only at a clean end of stream. */
    private fun readHeaderLine(source: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val next = source.read()
            if (next < 0) {
                if (line.size() == 0) return null
                throw GitFailure("`git cat-file --batch` returned a truncated header")
            }
            if (next == '\n'.code) return String(line.toByteArray(), Charsets.UTF_8)
            line.write(next)
        }
    }

    /** Exactly `size` blob bytes, followed by the mandatory record separator. */
    private fun readBlobBytes(source: InputStream, size: Long): ByteArray {
        if (size > MAX_BLOB_BYTES) {
            throw GitFailure("a reachable blob exceeds the ${MAX_BLOB_BYTES / (1024 * 1024)} MiB scan ceiling; the blob scan fails closed rather than skipping it")
        }
        val buffer = ByteArrayOutputStream(size.toInt().coerceAtLeast(0))
        val chunk = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = source.read(chunk, 0, minOf(chunk.size.toLong(), remaining).toInt())
            if (read < 0) throw GitFailure("`git cat-file --batch` ended before the declared blob size")
            buffer.write(chunk, 0, read)
            remaining -= read
        }
        val separator = source.read()
        if (separator < 0) throw GitFailure("`git cat-file --batch` omitted the record separator after a blob")
        if (separator != '\n'.code) throw GitFailure("`git cat-file --batch` returned an unexpected byte after a blob")
        return buffer.toByteArray()
    }

    private fun git(vararg args: String, errorMessage: String): ProcessResult =
        run(root, listOf("git") + args, errorMessage)

    /** Every historical path, the paths that ever held a gitlink, and a safe blob-id path hint. */
    data class HistoryScan(
        val paths: Set<String>,
        val gitlinkPaths: Set<String>,
        val blobPathHint: Map<String, String>,
    )

    /** The type and size Git reports for an object. */
    data class ObjectInfo(val type: String, val size: Long)
}

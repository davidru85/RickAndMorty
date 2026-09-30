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
 * Every Git command is run with an argument array — never an interpolated shell string — and
 * every result is read as bytes, so path names containing spaces, tabs, newlines or
 * non-ASCII characters survive unchanged. Git is invoked through [ProcessBuilder] rather than
 * Gradle's `Exec` support so that configuration cache never has to carry a captured process
 * result, and so that a large blob stream can be read incrementally.
 *
 * Every parser fails **closed**. A command whose contract requests `-z` must produce a stream
 * that ends with the record terminator, and each record must match its documented grammar; an
 * unexpected, truncated, extra or undecodable record raises [GitFailure] rather than being
 * skipped. A clean run therefore never means "the command produced less than it promised".
 *
 * Reachability has three sources, because a local ref may point at a commit, a tree or a blob:
 * the commit raw-history stream, the recursive contents of every ref that peels to a tree, and
 * the all-object list for blob content. Only the blob list decides what is scanned; the two
 * path sources decide what is classified.
 *
 * The identity contract is the Git top level, not the working directory: when Git reports a
 * top level different from the Gradle root, the repository is not the one being built and the
 * caller must fail (HYG-05).
 */
internal class GitRepository private constructor(private val root: File) {

    /** A Git failure that must fail the task closed rather than be read as zero findings. */
    class GitFailure(message: String) : IOException(message)

    /** A symlink that would have to be followed out of the repository (HYG-07). */
    class ExternalSymlink(val relativePath: String) : IOException()

    companion object {

        private const val ZERO_ID = "0000000000000000000000000000000000000000"
        private const val NULL_ID = "0000"
        private const val GITLINK_MODE = "160000"
        private const val CHUNK_BYTES = 256 * 1024
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
            val result = runCapturing(root, command) { }
            if (result.status != 0) {
                val detail = result.stderr.trim()
                throw GitFailure("$errorMessage: git exited with status ${result.status}${if (detail.isEmpty()) "" else ": $detail"}")
            }
            return ProcessResult(result.stdout)
        }

        private fun runCapturing(root: File, command: List<String>, feed: (java.io.OutputStream) -> Unit): Capture {
            val process = try {
                ProcessBuilder(command).directory(root).redirectErrorStream(false).start()
            } catch (e: IOException) {
                throw GitFailure("Git could not be started for `${command.firstOrNull()}` (${e.message})")
            }
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val outThread = Thread { drain(process.inputStream, stdout) }
            val errThread = Thread { drain(process.errorStream, stderr) }
            outThread.start()
            errThread.start()
            feed(process.outputStream)
            val status = process.waitFor()
            outThread.join()
            errThread.join()
            return Capture(status, stdout.toByteArray(), String(stderr.toByteArray(), Charsets.UTF_8))
        }

        private fun drain(input: InputStream, sink: ByteArrayOutputStream) {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return
                sink.write(buffer, 0, read)
            }
        }

        /**
         * A `-z` stream: empty, or terminated by the NUL record separator. A non-empty stream
         * without a final NUL is truncated evidence and fails closed.
         */
        private fun splitNul(bytes: ByteArray, what: String): List<ByteArray> {
            if (bytes.isEmpty()) return emptyList()
            if (bytes[bytes.size - 1] != 0.toByte()) {
                throw GitFailure("$what did not end with the record terminator; the stream is truncated (HYG-06)")
            }
            val records = mutableListOf<ByteArray>()
            var start = 0
            for (index in bytes.indices) {
                if (bytes[index] == 0.toByte()) {
                    if (index > start) records += bytes.copyOfRange(start, index)
                    start = index + 1
                }
            }
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

    private class Capture(val status: Int, val stdout: ByteArray, val stderr: String)

    /** The raw bytes a Git command produced, decoded leniently for reporting only. */
    class ProcessResult(internal val bytes: ByteArray) {
        fun text(): String = String(bytes, Charsets.UTF_8)
    }

    private val objectIdLength: Int by lazy {
        when (val format = git("rev-parse", "--show-object-format", errorMessage = "cannot read the object format").text().trim()) {
            "sha1" -> 40
            "sha256" -> 64
            else -> throw GitFailure("Git reported an unsupported object format; the scan cannot validate object ids (HYG-06)")
        }
    }

    /**
     * `git rev-parse --is-shallow-repository` (HYG-06). A shallow checkout hides history, so a
     * clean result there would be a false guarantee. Any answer that is not exactly `true` or
     * `false` is a failure, never silently read as "not shallow".
     */
    fun isShallow(): Boolean = when (
        val value = git("rev-parse", "--is-shallow-repository", errorMessage = "cannot read the checkout depth").text().trim()
    ) {
        "true" -> true
        "false" -> false
        else -> throw GitFailure("Git returned an unexpected answer for the checkout depth; the scan cannot proceed (HYG-06)")
    }

    /** The commit-eligible working set: tracked plus untracked, non-ignored, deduplicated. */
    fun workingSet(): List<String> {
        val bytes = git(
            "ls-files", "-z", "--cached", "--others", "--exclude-standard",
            errorMessage = "cannot enumerate the commit-eligible working set",
        ).bytes
        val paths = sortedSetOf<String>()
        splitNul(bytes, "the working-set listing").forEach { record ->
            val path = decode(record, "a commit-eligible path")
            if (path.isEmpty()) throw GitFailure("the working-set listing contains an empty path; refusing the stream")
            paths += path
        }
        return paths.toList()
    }

    /** The index mode per tracked path, so a symlink or gitlink is detected before it is read. */
    fun indexModes(): Map<String, String> {
        val bytes = git("ls-files", "-s", "-z", errorMessage = "cannot read the index").bytes
        val modes = linkedMapOf<String, String>()
        splitNul(bytes, "the index listing").forEach { record ->
            val text = decode(record, "an index entry")
            val tab = text.indexOf('\t')
            if (tab <= 0) throw GitFailure("the index contains a record without a path separator; refusing to skip it")
            val header = text.substring(0, tab).trim().split(' ').filter { it.isNotEmpty() }
            if (header.size != 3) throw GitFailure("the index contains a malformed record; refusing to skip it")
            val mode = header[0]
            if (mode.length != 6 || !mode.all { it in "01234567" }) {
                throw GitFailure("the index contains an entry with an unreadable mode; refusing to skip it")
            }
            requireObjectId(header[1], "the index")
            if (header[2].toIntOrNull() !in 0..3) {
                throw GitFailure("the index contains an entry with an unreadable stage; refusing to skip it")
            }
            val path = text.substring(tab + 1)
            if (path.isEmpty()) throw GitFailure("the index contains an entry without a path; refusing to skip it")
            modes[path] = mode
        }
        return modes
    }

    /** `git ls-files -ci --exclude-standard`: tracked paths that a rule now ignores (HYG-02). */
    fun trackedIgnoredPaths(): List<String> {
        val bytes = git(
            "ls-files", "-ci", "--exclude-standard", "-z",
            errorMessage = "cannot list tracked ignored paths",
        ).bytes
        return splitNul(bytes, "the tracked-ignored listing").map { decode(it, "a tracked ignored path") }.sorted()
    }

    /**
     * Resolves candidate paths against the ignore rules exactly as Git does, tracked or not,
     * and reports the deciding rule. `check-ignore` exits 0 when at least one path is ignored,
     * 1 when none is, and 128 on a real error; only the last is a failure. The output is a
     * sequence of four `-z` fields per matched path; any other arity fails closed.
     */
    fun ignoreStatus(candidates: List<String>): Map<String, IgnoreMatch> {
        if (candidates.isEmpty()) return emptyMap()
        val input = candidates.joinToString(separator = "") { "$it\u0000" }
        val capture = runCapturing(root, listOf("git", "check-ignore", "--no-index", "-v", "-z", "--stdin")) {
            it.use { stream -> stream.write(input.toByteArray(Charsets.UTF_8)) }
        }
        if (capture.status != 0 && capture.status != 1) {
            throw GitFailure("cannot evaluate the ignore rules: git exited with status ${capture.status}: ${capture.stderr.trim()}")
        }
        val records = splitNul(capture.stdout, "the ignore-rule output")
        if (records.size % 4 != 0) {
            throw GitFailure("the ignore-rule output has an unexpected record count; refusing to read it partially (HYG-01)")
        }
        val matches = linkedMapOf<String, IgnoreMatch>()
        var index = 0
        while (index < records.size) {
            val line = decode(records[index + 1], "an ignore-rule line number").trim().toIntOrNull()
                ?: throw GitFailure("the ignore-rule output has a non-numeric line number; refusing it (HYG-01)")
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
     * Streams a commit-eligible file's decoded content in bounded chunks. Symlinks are never
     * followed: a link whose target resolves outside the repository is an [ExternalSymlink];
     * a link inside it yields its target text, which is the repository data.
     */
    fun streamCandidate(relativePath: String, onChunk: (String) -> Unit) {
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
            onChunk(linkText)
            return
        }
        if (!file.isFile) {
            throw GitFailure("`$relativePath` is commit-eligible but is not a regular file")
        }
        streamFile(file, onChunk) { "cannot read `$relativePath`: ${it.message}" }
    }

    private fun streamFile(file: File, onChunk: (String) -> Unit, describe: (IOException) -> String) {
        try {
            Files.newInputStream(file.toPath()).use { input -> stream(input, onChunk) }
        } catch (e: IOException) {
            throw GitFailure(describe(e))
        }
    }

    private fun stream(input: InputStream, onChunk: (String) -> Unit) {
        val buffer = ByteArray(CHUNK_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            if (read > 0) onChunk(String(buffer, 0, read, Charsets.UTF_8))
        }
    }

    /**
     * Every object id reachable from every local ref, distinct and sorted. A direct ref to a
     * blob makes that blob reachable with no tree path, so this list is the complete candidate
     * set for the content scan; classification by [checkObjects] decides which are blobs.
     */
    fun reachableObjectIds(): List<String> {
        val bytes = git(
            "rev-list", "--objects", "--all", "-z",
            errorMessage = "cannot enumerate the objects reachable from local refs",
        ).bytes
        val ids = sortedSetOf<String>()
        splitNul(bytes, "the reachable-object listing").forEach { record ->
            val text = decode(record, "a reachable object record")
            when {
                text.startsWith("path=") -> if (text.length == "path=".length) {
                    throw GitFailure("a reachable object record has an empty path; refusing the stream (HYG-06)")
                }
                else -> ids += requireObjectId(text, "the reachable-object listing")
            }
        }
        return ids.toList()
    }

    /**
     * Blob types and sizes for every requested object id, in one `cat-file --batch-check`
     * process. The response count and order must match the request exactly; a missing, extra,
     * misordered or unparseable line is a failure, never a silent skip.
     */
    fun checkObjects(ids: List<String>): Map<String, ObjectInfo> {
        if (ids.isEmpty()) return emptyMap()
        val input = ids.joinToString(separator = "\n", postfix = "\n")
        val capture = runCapturing(
            root,
            listOf("git", "cat-file", "--batch-check=%(objectname) %(objecttype) %(objectsize)"),
        ) { it.use { stream -> stream.write(input.toByteArray(Charsets.UTF_8)) } }
        if (capture.status != 0) {
            throw GitFailure("cannot classify the reachable objects: git exited with status ${capture.status}: ${capture.stderr.trim()} (HYG-06)")
        }
        val text = String(capture.stdout, Charsets.UTF_8)
        if (text.isEmpty()) throw GitFailure("Git classified 0 of ${ids.size} requested object(s); the history scan is incomplete (HYG-06)")
        val lines = text.split('\n')
        if (lines.last().isNotEmpty()) throw GitFailure("the object classification does not end with a record separator; refusing it (HYG-06)")
        val records = lines.dropLast(1)
        if (records.size != ids.size) {
            throw GitFailure("Git classified ${records.size} of ${ids.size} requested object(s); the history scan is incomplete (HYG-06)")
        }
        val infos = linkedMapOf<String, ObjectInfo>()
        records.forEachIndexed { position, raw ->
            val parts = raw.trim().split(' ').filter { it.isNotEmpty() }
            if (parts.size != 3) throw GitFailure("Git returned an unreadable object classification; the history scan is incomplete (HYG-06)")
            if (parts[0] != ids[position]) throw GitFailure("the object classification does not match the request; the history scan is incomplete (HYG-06)")
            if (parts[1] !in OBJECT_TYPES) throw GitFailure("Git returned an unknown object type during classification (HYG-06)")
            val size = parts[2].toLongOrNull() ?: throw GitFailure("Git returned a non-numeric object size during classification (HYG-06)")
            if (size < 0) throw GitFailure("Git returned a negative object size during classification (HYG-06)")
            infos[parts[0]] = ObjectInfo(parts[1], size)
        }
        return infos
    }

    /**
     * Every historical path occurrence reachable through **commits**, with the paths that ever
     * carried a gitlink entry.
     *
     * `rev-list --objects` names an object once, so its single optional name is not an
     * exhaustive path history. This reads a NUL-safe raw history stream over
     * `--root -m --no-renames`, which yields one record per introduced, modified or deleted
     * path per commit — including root and merge commits — with full 40/64-hex modes so a
     * gitlink is visible. Rename detection is disabled, so a rename is a delete plus an add and
     * both paths appear.
     */
    fun commitHistoryScan(): HistoryScan {
        val bytes = git(
            "log", "--all", "--root", "--raw", "--no-renames", "-m", "-z", "--no-abbrev", "--format=",
            errorMessage = "cannot enumerate the historical paths",
        ).bytes
        val records = splitNul(bytes, "the raw history stream")
        val paths = sortedSetOf<String>()
        val gitlinkPaths = sortedSetOf<String>()
        val blobPathHint = linkedMapOf<String, String>()
        var index = 0
        while (index < records.size) {
            val header = decode(records[index], "a history-record header").trimStart('\n', '\r')
            if (!header.startsWith(":")) {
                throw GitFailure("the raw history stream contains a record that is not a path change; refusing it (HYG-04)")
            }
            val fields = header.substring(1).split(' ')
            if (fields.size != 5) throw GitFailure("a history record has an unreadable header; refusing it (HYG-04)")
            val status = fields[4]
            if (status.startsWith("R") || status.startsWith("C")) {
                throw GitFailure("a rename or copy record appeared although rename detection is disabled; refusing it (HYG-04)")
            }
            if (index + 1 >= records.size) throw GitFailure("a history record has no path; refusing the truncated stream (HYG-04)")
            val path = decode(records[index + 1], "a historical path")
            if (path.isEmpty()) throw GitFailure("a history record has an empty path; refusing the stream (HYG-04)")
            index += 2
            paths += path
            if (fields[0] == GITLINK_MODE || fields[1] == GITLINK_MODE) gitlinkPaths += path
            val destination = fields[3]
            if (destination != ZERO_ID && destination.length == objectIdLength) blobPathHint.putIfAbsent(destination, path)
        }
        return HistoryScan(paths, gitlinkPaths, blobPathHint)
    }

    /**
     * Every path inside a local ref that peels to a **tree** rather than a commit: a ref may
     * point directly at a tree, and `git log` does not enumerate the entries below it, so a
     * forbidden path there would otherwise never be classified. Lightweight refs and annotated
     * tag chains are both handled by peeling, and each unique root tree is enumerated once with
     * recursive `ls-tree`, keeping every name even when two paths share one blob.
     */
    fun directTreeScan(): HistoryScan {
        val paths = sortedSetOf<String>()
        val gitlinkPaths = sortedSetOf<String>()
        val blobPathHint = linkedMapOf<String, String>()
        val treeIds = sortedSetOf<String>()
        forEachRef().forEach { ref ->
            if (peel(ref, "^{commit}") != null) return@forEach
            val tree = peel(ref, "^{tree}") ?: return@forEach
            treeIds += tree
        }
        treeIds.forEach { treeId ->
            val bytes = git("ls-tree", "-r", "-z", "--full-tree", treeId, errorMessage = "cannot enumerate a direct tree ref").bytes
            splitNul(bytes, "a direct-tree listing").forEach { record ->
                val text = decode(record, "a direct-tree entry")
                val tab = text.indexOf('\t')
                if (tab <= 0) throw GitFailure("a direct-tree entry has no path separator; refusing it (HYG-04)")
                val header = text.substring(0, tab).split(' ')
                if (header.size != 3) throw GitFailure("a direct-tree entry has a malformed header; refusing it (HYG-04)")
                val mode = header[0]
                if (mode.length != 6 || !mode.all { it in "01234567" }) {
                    throw GitFailure("a direct-tree entry has an unreadable mode; refusing it (HYG-04)")
                }
                if (header[1] !in OBJECT_TYPES) throw GitFailure("a direct-tree entry has an unknown object type; refusing it (HYG-04)")
                val objectId = requireObjectId(header[2], "a direct-tree listing")
                val path = text.substring(tab + 1)
                if (path.isEmpty()) throw GitFailure("a direct-tree entry has an empty path; refusing it (HYG-04)")
                paths += path
                if (mode == GITLINK_MODE) gitlinkPaths += path
                if (header[1] == "blob") blobPathHint.putIfAbsent(objectId, path)
            }
        }
        return HistoryScan(paths, gitlinkPaths, blobPathHint)
    }

    /**
     * `refname\0objectname` for every local ref, deduplicated, in a stable order.
     *
     * `for-each-ref` terminates each record with a newline rather than a NUL, and a Git ref name
     * may not contain a newline or a NUL, so line parsing is lossless here. Each line must carry
     * exactly one NUL separator and a ref name that Git itself would accept.
     */
    private fun forEachRef(): List<String> {
        val bytes = git(
            "for-each-ref", "--format=%(refname)%00%(objectname)",
            errorMessage = "cannot enumerate the local refs",
        ).bytes
        val refs = sortedSetOf<String>()
        String(bytes, Charsets.UTF_8).lineSequence().forEach { line ->
            if (line.isEmpty()) return@forEach
            val separator = line.indexOf('\u0000')
            if (separator <= 0 || line.indexOf('\u0000', separator + 1) >= 0) {
                throw GitFailure("the ref listing contains a malformed record; refusing it (HYG-06)")
            }
            val name = line.substring(0, separator)
            if (name.isEmpty() || name.startsWith("/") || name.endsWith("/") || name.contains("..")) {
                throw GitFailure("the ref listing contains an invalid ref name; refusing it (HYG-06)")
            }
            requireObjectId(line.substring(separator + 1), "the ref listing")
            refs += name
        }
        return refs.toList()
    }

    /** The object a ref peels to under [suffix], or `null` when it does not peel that way. */
    private fun peel(ref: String, suffix: String): String? {
        val capture = runCapturing(root, listOf("git", "rev-parse", "--verify", "--quiet", "$ref$suffix")) { it.close() }
        if (capture.status != 0) return null
        val value = String(capture.stdout, Charsets.UTF_8).trim()
        if (value.isEmpty()) throw GitFailure("Git returned an empty peel target for a local ref; refusing it (HYG-06)")
        return requireObjectId(value, "a ref peel target")
    }

    /**
     * Streams every requested blob exactly once, in bounded chunks, and returns the number of
     * records actually consumed. Each response must match its request by id, be a `blob`, carry
     * a non-negative size and be followed by the record separator; the process must exit zero.
     * A short, long, mismatched or truncated stream raises [GitFailure] instead of reporting a
     * partial scan. There is no whole-object size ceiling: content is streamed, not buffered.
     */
    fun forEachBlob(
        ids: List<String>,
        onStart: (String) -> Unit,
        onChunk: (String) -> Unit,
        onEnd: () -> Unit,
    ): Int {
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
                    ?: throw GitFailure("`git cat-file --batch` ended after $consumed of ${ids.size} blob(s); the blob scan is incomplete (HYG-06)")
                val parts = header.trim().split(' ').filter { it.isNotEmpty() }
                if (parts.size != 3) throw GitFailure("`git cat-file --batch` returned an unreadable header; the blob scan is incomplete (HYG-06)")
                if (parts[0] != expected) throw GitFailure("`git cat-file --batch` returned a record for ${parts[0].take(12)}… while ${expected.take(12)}… was requested (HYG-06)")
                if (parts[1] != "blob") throw GitFailure("`git cat-file --batch` returned type `${parts[1]}` where `blob` was requested (HYG-06)")
                val size = parts[2].toLongOrNull() ?: throw GitFailure("`git cat-file --batch` returned a non-numeric blob size (HYG-06)")
                if (size < 0) throw GitFailure("`git cat-file --batch` returned a negative blob size (HYG-06)")
                onStart(expected)
                readBlob(source, size, onChunk)
                onEnd()
                consumed++
            }
            if (source.read() >= 0) throw GitFailure("`git cat-file --batch` returned more records than were requested (HYG-06)")
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

    /** Exactly `size` blob bytes streamed in chunks, followed by the mandatory separator. */
    private fun readBlob(source: InputStream, size: Long, onChunk: (String) -> Unit) {
        val buffer = ByteArray(CHUNK_BYTES)
        var remaining = size
        while (remaining > 0) {
            val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw GitFailure("`git cat-file --batch` ended before the declared blob size (HYG-06)")
            onChunk(String(buffer, 0, read, Charsets.UTF_8))
            remaining -= read
        }
        val separator = source.read()
        if (separator < 0) throw GitFailure("`git cat-file --batch` omitted the record separator after a blob (HYG-06)")
        if (separator != '\n'.code) throw GitFailure("`git cat-file --batch` returned an unexpected byte after a blob (HYG-06)")
    }

    /** A complete batch header line, or `null` only at a clean end of stream. */
    private fun readHeaderLine(source: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val next = source.read()
            if (next < 0) {
                if (line.size() == 0) return null
                throw GitFailure("`git cat-file --batch` returned a truncated header (HYG-06)")
            }
            if (next == '\n'.code) return String(line.toByteArray(), Charsets.UTF_8)
            line.write(next)
        }
    }

    private fun requireObjectId(value: String, what: String): String {
        if (value.length != objectIdLength || !value.all { it in "0123456789abcdef" }) {
            throw GitFailure("$what contains an object id that is not valid for this repository's object format (HYG-06)")
        }
        return value
    }

    private fun git(vararg args: String, errorMessage: String): ProcessResult =
        run(root, listOf("git") + args, errorMessage)

    /** Paths, gitlinks and a safe blob-id path hint gathered from one reachability source. */
    data class HistoryScan(
        val paths: Set<String>,
        val gitlinkPaths: Set<String>,
        val blobPathHint: Map<String, String>,
    ) {
        /** Merges two sources, so distinct sources can never hide a path or a gitlink. */
        fun merge(other: HistoryScan): HistoryScan = HistoryScan(
            paths = paths + other.paths,
            gitlinkPaths = gitlinkPaths + other.gitlinkPaths,
            blobPathHint = (blobPathHint + other.blobPathHint),
        )
    }

    /** The type and size Git reports for an object. */
    data class ObjectInfo(val type: String, val size: Long)
}

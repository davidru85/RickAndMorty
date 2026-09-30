package io.github.davidru85.multiverse.buildlogic.hygiene

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The Git-backed view of the repository that `verifyRepositoryHygiene` scans
 * (`TEST-UNIT-026`, `REQ-SEC-002`, `AC-REQ-SEC-002-1`; DEC-062).
 *
 * Every Git command is run with an argument array — never an interpolated shell string —
 * and every result is read as bytes, so path names containing spaces, tabs, newlines or
 * non-ASCII bytes survive unchanged. Git is invoked through [ProcessBuilder] rather than
 * Gradle's `Exec` support so that configuration cache never has to carry a captured
 * process result, and so that a large blob stream can be read without materialising it as
 * one string.
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

        /** NUL-separated byte records, lossless for every legal path name. */
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
    }

    /** The raw bytes a Git command produced, decoded leniently for reporting only. */
    class ProcessResult(internal val bytes: ByteArray) {
        fun text(): String = String(bytes, Charsets.UTF_8)
    }

    fun root(): File = root

    /**
     * `git rev-parse --is-shallow-repository` (HYG-06). A shallow checkout hides history,
     * so a clean result there would be a false guarantee.
     */
    fun isShallow(): Boolean =
        git("rev-parse", "--is-shallow-repository", errorMessage = "cannot read the checkout depth")
            .text().trim() == "true"

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
        splitNul(bytes).forEach { paths += String(it, Charsets.UTF_8) }
        return paths.toList()
    }

    /** The index mode per path, so a symlink or gitlink is detected before its bytes are read. */
    fun indexModes(): Map<String, String> {
        val bytes = git("ls-files", "-s", "-z", errorMessage = "cannot read the index").bytes
        val modes = linkedMapOf<String, String>()
        splitNul(bytes).forEach { record ->
            val text = String(record, Charsets.UTF_8)
            val tab = text.indexOf('\t')
            if (tab <= 0) return@forEach
            val mode = text.substring(0, tab).trim().substringBefore(' ')
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
        return splitNul(bytes).map { String(it, Charsets.UTF_8) }.sorted()
    }

    /**
     * Resolves candidate paths against the ignore rules exactly as Git does, tracked or not,
     * and reports the deciding rule. `check-ignore` exits 0 when at least one path is
     * ignored, 1 when none is, and 128 on a real error; only the last is a failure.
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
        val matches = linkedMapOf<String, IgnoreMatch>()
        val records = splitNul(stdout.toByteArray())
        var index = 0
        while (index + 3 < records.size) {
            matches[String(records[index + 3], Charsets.UTF_8)] = IgnoreMatch(
                source = String(records[index], Charsets.UTF_8),
                line = String(records[index + 1], Charsets.UTF_8).trim().toIntOrNull() ?: 0,
                pattern = String(records[index + 2], Charsets.UTF_8),
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
     * Every object reachable from every local ref, as (object id, path) pairs. The NUL-safe
     * enumeration keeps a path containing a newline intact, and a record without a path is a
     * commit, tree, tag or an already-named blob.
     */
    fun reachableObjects(): List<ReachableObject> {
        val bytes = git(
            "rev-list",
            "--objects",
            "--all",
            "-z",
            errorMessage = "cannot enumerate the objects reachable from local refs",
        ).bytes
        val records = splitNul(bytes)
        val objects = mutableListOf<ReachableObject>()
        var index = 0
        while (index < records.size) {
            val id = String(records[index], Charsets.UTF_8).trim()
            index++
            if (id.isEmpty()) continue
            var path: String? = null
            if (index < records.size) {
                val entry = String(records[index], Charsets.UTF_8)
                if (entry.startsWith("path=")) {
                    path = entry.removePrefix("path=")
                    index++
                }
            }
            objects += ReachableObject(id, path)
        }
        return objects
    }

    /**
     * Blob types and sizes for the given object ids, in one `cat-file --batch-check`
     * process. A missing or ambiguous object is a failure, never a silent skip.
     */
    fun checkObjects(ids: List<String>): Map<String, ObjectInfo> {
        if (ids.isEmpty()) return emptyMap()
        val input = ids.joinToString(separator = "\n", postfix = "\n")
        val output = runWithInput(
            command = listOf("git", "cat-file", "--batch-check=%(objectname) %(objecttype) %(objectsize)"),
            input = input.toByteArray(Charsets.UTF_8),
            errorMessage = "cannot classify the reachable objects",
        ).text()
        val infos = linkedMapOf<String, ObjectInfo>()
        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(' ')
            if (parts.size != 3) throw GitFailure("Git could not classify a reachable object: $line")
            val size = parts[2].toLongOrNull() ?: throw GitFailure("Git reported a non-numeric object size: $line")
            infos[parts[0]] = ObjectInfo(parts[1], size)
        }
        return infos
    }

    /**
     * Streams every requested blob exactly once and hands its raw bytes to [consume].
     * Reading through `--batch` avoids one process per blob and preserves binary content,
     * so a credential inside a key container is inspected rather than skipped.
     */
    fun forEachBlob(ids: List<String>, consume: (ByteArray) -> Unit) {
        if (ids.isEmpty()) return
        val process = try {
            ProcessBuilder(listOf("git", "cat-file", "--batch")).directory(root).redirectErrorStream(false).start()
        } catch (e: IOException) {
            throw GitFailure("cannot read reachable blobs: Git could not be started (${e.message})")
        }
        val stderr = ByteArrayOutputStream()
        val errThread = Thread { drain(process.errorStream, stderr) }
        var complete = false
        errThread.start()
        try {
            process.outputStream.use { it.write(ids.joinToString(separator = "\n", postfix = "\n").toByteArray(Charsets.UTF_8)) }
            val source = process.inputStream
            while (true) {
                val header = readLine(source) ?: break
                if (header.isEmpty()) break
                val parts = header.split(' ')
                if (parts.size < 3) throw GitFailure("unexpected `git cat-file --batch` header: $header")
                val size = parts[2].toLongOrNull() ?: throw GitFailure("non-numeric blob size in header: $header")
                consume(readExactly(source, size))
            }
            complete = true
        } finally {
            if (!complete) process.destroyForcibly()
            process.waitFor()
            errThread.join()
            val detail = String(stderr.toByteArray(), Charsets.UTF_8).trim()
            if (detail.isNotEmpty()) throw GitFailure("`git cat-file --batch` reported: $detail")
        }
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

    private fun readLine(source: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val next = source.read()
            if (next < 0) return if (line.size() == 0) null else String(line.toByteArray(), Charsets.UTF_8)
            if (next == '\n'.code) return String(line.toByteArray(), Charsets.UTF_8)
            line.write(next)
        }
    }

    private fun readExactly(source: InputStream, size: Long): ByteArray {
        val buffer = ByteArrayOutputStream(size.toInt().coerceAtLeast(0))
        val chunk = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = source.read(chunk, 0, minOf(chunk.size.toLong(), remaining).toInt())
            if (read < 0) throw GitFailure("`git cat-file --batch` ended before the declared blob size")
            buffer.write(chunk, 0, read)
            remaining -= read
        }
        // `cat-file --batch` separates records with a single newline.
        val separator = source.read()
        if (separator >= 0 && separator != '\n'.code) throw GitFailure("unexpected byte after a blob record")
        return buffer.toByteArray()
    }

    private fun git(vararg args: String, errorMessage: String): ProcessResult =
        run(root, listOf("git") + args, errorMessage)

    /** An object reachable from a local ref, with its path when the enumeration supplied one. */
    data class ReachableObject(val id: String, val path: String?)

    /** The type and size Git reports for an object. */
    data class ObjectInfo(val type: String, val size: Long)
}

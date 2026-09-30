package io.github.davidru85.multiverse.buildlogic.hygiene

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * `verifyRepositoryHygiene` — `TEST-UNIT-026` (`REQ-SEC-002`, `AC-REQ-SEC-002-1`; DEC-062).
 *
 * One run does four things:
 *
 * 1. it holds the repository-path rules `HYG-01`…`HYG-07` over the tracked tree and the
 *    ignore rules;
 * 2. it scans the **commit-eligible working set** — every tracked file plus every untracked,
 *    non-ignored file — for the twelve credential classes;
 * 3. it scans **every unique blob reachable from every local ref** for the same classes, after
 *    the caller has fetched, so history is not silently missing;
 * 4. it fails closed on any Git error, a shallow checkout, a symlink that would leave the
 *    repository, a submodule and any object it cannot classify.
 *
 * Nothing it reports ever contains the text it matched: a finding names a rule id, a
 * root-relative location, a line number where one exists and the blob identity for history.
 *
 * The task reads Git and the working tree at execution time and declares no outputs, so it is
 * never up to date and never answers from a re-used configuration-cache or build-cache entry.
 */
@DisableCachingByDefault(because = "scans mutable Git state and the working tree; a cached security result would be a false guarantee")
abstract class RepositoryHygieneTask : DefaultTask() {

    /** The Gradle root that Git must also report as its work-tree top level (HYG-05). */
    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = repositoryRoot.get().asFile
        val git = openRepository(root)

        val log = HygieneViolationLog()

        // HYG-06 — an incomplete checkout cannot certify anything, so it fails before scanning.
        if (git.isShallow()) {
            throw GradleException(
                "HYG-06: `${root.path}` is a shallow Git checkout, so its history is incomplete and a " +
                    "clean result would be a false guarantee. Fetch the full history (for example `git " +
                    "fetch --unshallow`, or `fetch-depth: 0` in CI) and run `verifyRepositoryHygiene` " +
                    "again (TEST-UNIT-026).",
            )
        }
        logger.lifecycle("verifyRepositoryHygiene: Git work tree confirmed at ${root.path} (non-shallow).")

        checkIgnoreRules(git, log)
        val modes = readIndexModes(git)
        val workingSet = readWorkingSet(git)
        checkTrackedPaths(modes, log)
        checkTrackedIgnored(git, log)
        scanWorkingSet(git, workingSet, modes, log)
        val history = scanHistory(git, log)

        if (!log.isEmpty()) throw GradleException(log.render())

        logger.lifecycle(
            "verifyRepositoryHygiene passed: ${workingSet.size} commit-eligible path(s) scanned, " +
                "${history.objectsConsidered} reachable object(s) considered, " +
                "${history.blobsScanned} unique blob(s) scanned from all local refs, " +
                "${HygieneRules.RULE_IDS.size} credential rule classes, ${HygieneRules.REQUIRED_IGNORED.size} " +
                "ignore sentinels, 0 findings (TEST-UNIT-026, REQ-SEC-002, AC-REQ-SEC-002-1).",
        )
    }

    private fun openRepository(root: File): GitRepository = try {
        GitRepository.open(root)
    } catch (e: GitRepository.GitFailure) {
        throw GradleException("HYG-05/HYG-06: ${e.message} (TEST-UNIT-026)", e)
    }

    /** HYG-01 — the required paths are ignored and the shared project files are not. */
    private fun checkIgnoreRules(git: GitRepository, log: HygieneViolationLog) {
        val candidates = HygieneRules.REQUIRED_IGNORED.map { it.path } + HygieneRules.REQUIRED_TRACKABLE
        val matches = try {
            git.ignoreStatus(candidates)
        } catch (e: GitRepository.GitFailure) {
            throw GradleException("HYG-01: ${e.message} (TEST-UNIT-026)", e)
        }
        HygieneRules.REQUIRED_IGNORED.forEach { candidate ->
            if (candidate.path !in matches) {
                log.add(
                    "HYG-01",
                    candidate.path,
                    "must stay ignored (${candidate.category}) but no `.gitignore` rule matches it; " +
                        "restore the rule that covers this path",
                )
            }
        }
        HygieneRules.REQUIRED_TRACKABLE.forEach { candidate ->
            matches[candidate]?.let { match ->
                log.add(
                    "HYG-01",
                    candidate,
                    "is shared project content but `${match.source}:${match.line}` (`${match.pattern}`) " +
                        "now ignores it; narrow that rule",
                )
            }
        }
    }

    /** The index mode per tracked path, so a symlink or a gitlink is found before it is read. */
    private fun readIndexModes(git: GitRepository): Map<String, String> = try {
        git.indexModes()
    } catch (e: GitRepository.GitFailure) {
        throw GradleException("HYG-03/HYG-07: ${e.message} (TEST-UNIT-026)", e)
    }

    private fun readWorkingSet(git: GitRepository): List<String> = try {
        git.workingSet()
    } catch (e: GitRepository.GitFailure) {
        throw GradleException("HYG-06: ${e.message} (TEST-UNIT-026)", e)
    }

    /** HYG-03 — no tracked path belongs to a prohibited class. */
    private fun checkTrackedPaths(modes: Map<String, String>, log: HygieneViolationLog) {
        modes.keys.forEach { path ->
            HygieneRules.classifyProhibited(path)?.let { className ->
                log.add(
                    "HYG-03",
                    path,
                    "is tracked but belongs to $className; remove it from the index " +
                        "(`git rm --cached`) and rely on `.gitignore`",
                )
            }
        }
    }

    /** HYG-02 — a tracked file an ignore rule now matches would vanish from the next clone. */
    private fun checkTrackedIgnored(git: GitRepository, log: HygieneViolationLog) {
        val trackedIgnored = try {
            git.trackedIgnoredPaths()
        } catch (e: GitRepository.GitFailure) {
            throw GradleException("HYG-02: ${e.message} (TEST-UNIT-026)", e)
        }
        trackedIgnored.forEach { path ->
            log.add(
                "HYG-02",
                path,
                "is tracked and also ignored; the file would disappear from a fresh clone. " +
                    "Untrack it or remove the rule that hides it",
            )
        }
    }

    /**
     * The commit-eligible working set: each path is classified, then read without following a
     * symlink, then scanned for the twelve credential classes.
     */
    private fun scanWorkingSet(
        git: GitRepository,
        paths: List<String>,
        modes: Map<String, String>,
        log: HygieneViolationLog,
    ) {
        paths.forEach { path ->
            if (modes[path] == GITLINK_MODE) {
                log.add(
                    "HYG-07",
                    path,
                    "is a submodule/gitlink; a submodule hides a second history that this check does " +
                        "not scan, so it is unsupported here",
                )
                return@forEach
            }
            HygieneRules.pathRule(path)?.let { ruleId ->
                log.add(ruleId, path, "the path name itself is a credential carrier and must not be commit-eligible")
            }
            val bytes = try {
                git.readCandidate(path)
            } catch (e: GitRepository.ExternalSymlink) {
                log.add(
                    "HYG-07",
                    path,
                    "is a symbolic link whose target leaves the repository; the check never follows it",
                )
                return@forEach
            } catch (e: GitRepository.GitFailure) {
                throw GradleException("HYG-07: ${e.message} (TEST-UNIT-026)", e)
            }
            scanBytes(bytes, location = path, blobId = null, log = log)
        }
    }

    /**
     * Every unique blob reachable from every local ref, scanned exactly once. Paths are checked
     * first (HYG-04), then the bytes are scanned for content rules.
     */
    private fun scanHistory(git: GitRepository, log: HygieneViolationLog): HistoryCounts {
        val reachable = try {
            git.reachableObjects()
        } catch (e: GitRepository.GitFailure) {
            throw GradleException("HYG-06: ${e.message} (TEST-UNIT-026)", e)
        }

        val ids = reachable.map { it.id }.distinct().sorted()
        val infos = try {
            git.checkObjects(ids)
        } catch (e: GitRepository.GitFailure) {
            throw GradleException("HYG-06: ${e.message} (TEST-UNIT-026)", e)
        }
        val missing = ids.filterNot { it in infos }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "HYG-06: Git could not classify ${missing.size} reachable object(s), starting with " +
                    "`${missing.first()}`; the history scan is incomplete (TEST-UNIT-026).",
            )
        }

        // Blob id -> the historical paths that ever carried it; and the full historical path set.
        val pathsByBlob = linkedMapOf<String, MutableSet<String>>()
        val historyPaths = sortedSetOf<String>()
        reachable.forEach { entry ->
            val path = entry.path ?: return@forEach
            historyPaths += path
            if (infos[entry.id]?.type == "blob") pathsByBlob.getOrPut(entry.id) { sortedSetOf() } += path
        }

        // HYG-04 — a prohibited or credential-carrier path in reachable history is a finding, and
        // the credential class that owns the name is reported with it so both families agree.
        historyPaths.forEach { path ->
            val className = HygieneRules.classifyProhibited(path)
            val reason = when {
                HygieneRules.isCarrier(path) -> "is a credential-carrier path"
                HygieneRules.isEnvironmentFile(path) -> "is an environment file"
                className != null -> "belongs to $className"
                else -> null
            }
            if (reason != null) {
                log.add("HYG-04", path, "$reason and appears in reachable history; it must never have been committed")
                HygieneRules.pathRule(path)?.let { ruleId ->
                    log.add(ruleId, "$path@history", "the path name is a credential carrier in reachable history")
                }
            }
        }

        // Deterministic blob order, one `cat-file --batch` process for the whole history. The ids
        // are consumed in the same order the stream yields blobs, so identity needs no re-read.
        val blobIds = pathsByBlob.keys.sorted()
        val identity = blobIds.iterator()
        try {
            git.forEachBlob(blobIds) { bytes ->
                val blobId = if (identity.hasNext()) identity.next() else null
                val location = blobId?.let { id ->
                    pathsByBlob[id]?.firstOrNull()?.let { "$it@" } ?: ""
                } ?: ""
                scanBytes(bytes, location = location, blobId = blobId, log = log)
            }
        } catch (e: GitRepository.GitFailure) {
            throw GradleException("HYG-06: ${e.message} (TEST-UNIT-026)", e)
        }

        return HistoryCounts(objectsConsidered = ids.size, blobsScanned = blobIds.size)
    }

    /**
     * Applies every content rule to raw bytes. The bytes are decoded leniently, which keeps
     * ASCII credential shapes visible in a binary container; a rule that matches reports the
     * path or the blob identity, never the matched value.
     */
    private fun scanBytes(bytes: ByteArray, location: String, blobId: String?, log: HygieneViolationLog) {
        val text = String(bytes, Charsets.UTF_8)
        val base = when {
            location.isNotEmpty() && blobId != null -> "$location blob ${blobId.take(12)}"
            location.isNotEmpty() -> location
            blobId != null -> "blob ${blobId.take(12)}"
            else -> "unknown"
        }
        HygieneRules.scanContent(text) { ruleId, line ->
            log.add(ruleId, "$base:$line", "matches the $ruleId credential pattern")
        }
    }

    private companion object {
        const val GITLINK_MODE = "160000"
    }

    /** Safe aggregate counts, never content. */
    private data class HistoryCounts(val objectsConsidered: Int, val blobsScanned: Int)
}

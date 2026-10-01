package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * The live-host guard `TEST-UNIT-024` (`TESTING.md` §4.1, `REQ-NFR-005`).
 *
 * No test source set may carry a live API host literal: the remote boundary is always Ktor
 * `MockEngine` or a fake seam, and a test that names the real host is a test that will one day
 * contact it. The only source set allowed to reach `rickandmortyapi.com` is `contract-live`,
 * which belongs to the scheduled, non-blocking job. The guard is **fail-closed**: the moment a
 * `contract-live` source set exists, it is exempted by its own rule rather than by a widening
 * allow-list, and every other test source set stays scanned.
 *
 * The detector reports a path and a line, never the matched text, so a false positive cannot leak
 * anything into a build log (`SECURITY.md` §4.3's redaction rule, applied by analogy).
 */
internal object LiveHostGuard {

    /** The live hosts the app is allowed to talk to; the guard rejects them inside test sources. */
    private val LIVE_HOSTS = listOf("rickandmortyapi.com")

    /** The one source-set directory exempt from the scan, owned by the scheduled contract job. */
    const val SCHEDULED_SOURCE_SET = "contract-live"

    /**
     * The same source set as Kotlin names it: a Kotlin source set's directory is its identifier,
     * so the scheduled set appears as `contractLive` on disk. Both spellings are the scheduled job
     * and neither is a licence for any other source set (`DEC-070` names the directory).
     */
    private val SCHEDULED_SOURCE_SET_ALIASES = setOf(SCHEDULED_SOURCE_SET, "contractLive")

    /** One offending location: a repository-relative path and a 1-based line. */
    data class Finding(val path: String, val line: Int, val host: String)

    /**
     * Scans [roots] and reports every live-host literal found in a test source set.
     *
     * @param roots the directories to scan, already limited to source trees (never `build/`).
     */
    fun scan(files: Collection<File>, repositoryRoot: File): List<Finding> {
        val findings = mutableListOf<Finding>()
        files.filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.invariantSeparatorsPath }
            .forEach { file ->
                // The path the exemption decides on is repository-relative, so a file's own path
                // keeps the `contract-live` segment that a parent directory would erase.
                val relative = file.relativeTo(repositoryRoot).invariantSeparatorsPath
                if (isExempt(relative)) return@forEach
                file.readLines().forEachIndexed { index, line ->
                    val host = LIVE_HOSTS.firstOrNull { line.contains(it) } ?: return@forEachIndexed
                    findings += Finding(path = relative, line = index + 1, host = host)
                }
            }
        return findings
    }

    /**
     * Whether [file] belongs to the one source set the scheduled contract job owns.
     *
     * The exemption is by directory identity, not by a substring match on the path: only a
     * `contract-live` segment makes a test source set exempt.
     */
    private fun isExempt(repositoryRelativePath: String): Boolean {
        val segments = repositoryRelativePath.split('/')
        return segments.any { it in SCHEDULED_SOURCE_SET_ALIASES }
    }
}

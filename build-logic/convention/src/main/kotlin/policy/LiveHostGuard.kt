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

    /** One offending location: a repository-relative path and a 1-based line. */
    data class Finding(val path: String, val line: Int, val host: String)

    /**
     * Scans [roots] and reports every live-host literal found in a test source set.
     *
     * @param roots the directories to scan, already limited to source trees (never `build/`).
     */
    fun scan(roots: Collection<File>): List<Finding> {
        val findings = mutableListOf<Finding>()
        roots.filter { it.isDirectory }.sortedBy { it.path }.forEach { root ->
            root.walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .sortedBy { it.path }
                .forEach { file ->
                    if (isExempt(file, root)) return@forEach
                    file.readLines().forEachIndexed { index, line ->
                        val host = LIVE_HOSTS.firstOrNull { line.contains(it) } ?: return@forEachIndexed
                        // A comment that *names* the rule is still a literal the next editor can
                        // copy into a request, so it is reported too; the exception is a line whose
                        // only occurrence is the rule id itself, which the guard cannot match.
                        findings += Finding(
                            path = file.relativeTo(root).invariantSeparatorsPath,
                            line = index + 1,
                            host = host,
                        )
                    }
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
    private fun isExempt(file: File, root: File): Boolean =
        file.relativeTo(root).invariantSeparatorsPath.split('/').any { it == SCHEDULED_SOURCE_SET }
}

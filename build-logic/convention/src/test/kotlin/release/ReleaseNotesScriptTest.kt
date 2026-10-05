package io.github.davidru85.multiverse.buildlogic.release

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-064` — `tools/release-notes.sh` renders the release note `CONTRIBUTING.md` §3.6
 * specifies (`TASK-066`, `DEC-042`).
 *
 * Each case builds a throwaway repository with the commits it needs, copies the real script into
 * it and runs the script there, so the note is asserted on exactly the history it is derived from.
 */
class ReleaseNotesScriptTest {

    private val script =
        File(System.getProperty("user.dir"))
            .let { run -> generateSequence(run) { it.parentFile }.first { File(it, "tools/release-notes.sh").isFile } }
            .resolve("tools/release-notes.sh")

    private class Repository(val root: File) {
        private companion object {
            val IDENTITY =
                listOf(
                    "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
                    "-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false", "-c", "core.hooksPath=/dev/null",
                )
        }

        fun git(vararg arguments: String): String {
            val process =
                ProcessBuilder(
                    // The machine's own signing and hook settings are not part of the fixture.
                    listOf("git") + IDENTITY + arguments,
                ).directory(root)
                    .redirectErrorStream(true)
                    .start()
            val text = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), "git ${arguments.toList()} failed: $text")
            return text
        }

        fun commit(subject: String, body: String? = null) {
            val message = if (body == null) listOf("-m", subject) else listOf("-m", subject, "-m", body)
            git("commit", "--quiet", "--allow-empty", *message.toTypedArray())
        }

        fun notes(vararg arguments: String): Pair<Int, String> {
            val process =
                ProcessBuilder(listOf("bash", "tools/release-notes.sh") + arguments)
                    .directory(root)
                    .redirectErrorStream(true)
                    .start()
            val text = process.inputStream.bufferedReader().readText()
            return process.waitFor() to text.trim()
        }
    }

    private fun repository(): Repository {
        val root = kotlin.io.path.createTempDirectory("release-notes").toFile()
        script.copyTo(File(root, "tools/release-notes.sh"))
        return Repository(root).apply { git("init", "--quiet") }
    }

    private fun section(title: String, vararg entries: String): String =
        (listOf("## $title") + entries.map { "- $it" }).joinToString("\n")

    private fun note(vararg sections: String): String = sections.joinToString("\n\n")

    @Test
    fun `TEST-UNIT-064 the release types lead and every other type is maintenance`() {
        val repository = repository()
        listOf(
            "feat(discovery): browse every character",
            "fix(core-data): keep the filter on the next page",
            "perf(core-data): reuse the decoded page",
            "refactor(core-data): name the pager seam",
            "test(core-data): add failing test for the filter",
            "docs(docs): record the decision",
            "build(build-logic): pin the catalog",
            "ci(ci): run the gate on both runners",
            "chore(repo): tidy the ignore list",
            "revert: drop the experimental cache",
            "Update the readme",
        ).forEach { repository.commit(it) }

        assertEquals(
            0 to
                note(
                    section("Features", "browse every character"),
                    section("Fixes", "keep the filter on the next page"),
                    section("Performance", "reuse the decoded page"),
                    section(
                        "Maintenance",
                        "revert: drop the experimental cache",
                        "chore(repo): tidy the ignore list",
                        "ci(ci): run the gate on both runners",
                        "build(build-logic): pin the catalog",
                        "docs(docs): record the decision",
                        "test(core-data): add failing test for the filter",
                        "refactor(core-data): name the pager seam",
                    ),
                ),
            repository.notes("--all"),
        )
    }

    @Test
    fun `TEST-UNIT-064 a breaking change leads the note with what breaks`() {
        val repository = repository()
        repository.commit("feat(core-data)!: drop the v1 cache")
        repository.commit(
            "fix(core-data): rename the favourites key",
            "BREAKING CHANGE: a favourite stored by 0.1.0 is not read; favourite it again.",
        )
        repository.commit("feat(discovery): add the status filter")

        assertEquals(
            0 to
                note(
                    section(
                        "Breaking changes",
                        "rename the favourites key — a favourite stored by 0.1.0 is not read; favourite it again.",
                        "drop the v1 cache",
                    ),
                    section("Features", "add the status filter"),
                ),
            repository.notes("--all"),
        )
    }

    @Test
    fun `TEST-UNIT-064 by default the range starts at the previous tag`() {
        val repository = repository()
        repository.commit("feat(discovery): browse every character")
        repository.git("tag", "v0.1.0")
        repository.commit("fix(core-data): keep the filter on the next page")

        val expected = 0 to note(section("Fixes", "keep the filter on the next page"))
        assertEquals(expected, repository.notes())
        // Tagging the release commit does not change its own note.
        repository.git("tag", "v0.2.0")
        assertEquals(expected, repository.notes())
    }

    @Test
    fun `TEST-UNIT-064 the first release has no previous tag and covers the whole history`() {
        val repository = repository()
        repository.commit("feat(discovery): browse every character")
        repository.commit("fix(core-data): keep the filter on the next page")

        assertEquals(
            0 to note(section("Features", "browse every character"), section("Fixes", "keep the filter on the next page")),
            repository.notes(),
        )
    }

    @Test
    fun `TEST-UNIT-064 a range of maintenance only lists none of it`() {
        val repository = repository()
        repository.commit("feat(discovery): browse every character")
        repository.git("tag", "v0.1.0")
        repository.commit("docs(docs): record the decision")
        repository.commit("test(core-data): add failing test for the filter")

        assertEquals(
            0 to "No feature, fix, performance or breaking change: the range holds 2 maintenance commit(s).",
            repository.notes(),
        )
    }

    @Test
    fun `TEST-UNIT-064 an explicit range is honoured and a malformed call is a usage error`() {
        val repository = repository()
        repository.commit("feat(discovery): browse every character")
        repository.git("tag", "v0.1.0")
        repository.commit("fix(core-data): keep the filter on the next page")
        repository.git("tag", "v0.1.1")
        repository.commit("perf(core-data): reuse the decoded page")

        assertEquals(0 to note(section("Fixes", "keep the filter on the next page")), repository.notes("v0.1.0", "v0.1.1"))
        assertEquals(2, repository.notes("v0.1.0").first)
    }
}

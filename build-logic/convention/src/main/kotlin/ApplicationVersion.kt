// Application convention plugin for the Android shell.
package io.github.davidru85.multiverse.buildlogic

import org.gradle.api.Project

/**
 * The repository's single version source (`AC-REQ-NFR-006-2`, `DEC-043`, `DEC-067`).
 *
 * The `VERSION` file at the repository root holds one `MAJOR.MINOR.PATCH` value; the Android
 * `versionName` is that value verbatim, and `TASK-051` wires the same source to the real iOS
 * `CFBundleShortVersionString`. Validity is owned by the `verifyDependencyPins` rule
 * (`TEST-UNIT-014`), so this reader only supplies the value.
 */
internal object ApplicationVersion {

    /**
     * The `VERSION` value with its trailing newline removed, or `null` when the file is absent.
     * A `null` result is a build failure for the caller: an application cannot be assembled
     * without the single version source.
     */
    fun read(project: Project): String? {
        val file = project.rootProject.layout.projectDirectory.file("VERSION").asFile
        return if (file.isFile) file.readText().trimEnd('\n', '\r') else null
    }
}

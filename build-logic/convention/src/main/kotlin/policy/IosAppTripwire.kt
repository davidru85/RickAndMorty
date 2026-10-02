package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * The restoration tripwire of the temporary iOS suspension (`DEC-083`).
 *
 * The `ios` pull-request job is suspended until the repository contains a buildable iOS app. The
 * condition is read from the real Xcode project metadata — a `project.pbxproj` inside an `.xcodeproj` under `iosApp/`
 * that declares a native target of product type `com.apple.product-type.application` — never from a
 * flag someone can flip. The first change that introduces such a target (`TASK-051`) therefore has to
 * restore the `ios` job, its native suites and the native contract replay in the same change.
 *
 * A project with only framework, library or test-bundle targets does not end the suspension: the
 * Kotlin framework export of `TASK-078` is not the app.
 */
internal object IosAppTripwire {
    /** The directory the iOS app lives in (`DESIGN.md` §3, ADR-0012). */
    const val IOS_APP_DIRECTORY = "iosApp"

    private val APPLICATION_TARGET = Regex("""productType\s*=\s*"com\.apple\.product-type\.application"\s*;""")

    /** The repository-relative project files that declare an application target, sorted. */
    fun applicationProjects(root: File): List<String> =
        File(root, IOS_APP_DIRECTORY)
            .takeIf { it.isDirectory }
            ?.walkTopDown()
            ?.filter { it.isFile && it.name == "project.pbxproj" && it.parentFile.name.endsWith(".xcodeproj") }
            ?.filter { APPLICATION_TARGET.containsMatchIn(it.readText()) }
            ?.map { it.relativeTo(root).invariantSeparatorsPath }
            ?.sorted()
            ?.toList()
            .orEmpty()
}

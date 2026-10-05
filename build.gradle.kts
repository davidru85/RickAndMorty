// Root build script.
//
// It declares the third-party plugin aliases so that every module build script
// resolves them from the catalog, and applies the two policy families, whose checks are
// wired into `check`:
//
// - `multiverse.dependency.policy` turns the catalog pins, the per-entry rationale and the
//   README inventory into build checks (DEC-061, TEST-UNIT-013/014/051);
// - `multiverse.repository.hygiene` holds the tracked-path and credential rules over the
//   commit-eligible working set and every blob reachable from all local refs
//   (DEC-062, TEST-UNIT-026, REQ-SEC-002);
// - `multiverse.module.boundaries` turns the accepted module graph of ADR-0001 as amended
//   (DEC-066, DEC-069) into an executable policy: project edges, source-set kinds, the
//   :core:domain external allow-list and the staged destination/package rules
//   (TASK-017, TEST-UNIT-017/012/043, REQ-NFR-001, REQ-NFR-009).
//
// There is no `allprojects {}` or `subprojects {}` block anywhere in this build (OD-3).

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.ktlint) apply false
    id("multiverse.dependency.policy")
    id("multiverse.repository.hygiene")
    id("multiverse.module.boundaries")
    alias(libs.plugins.dependency.analysis)
}

// DEC-077 (`TASK-029`): dependency analysis is blocking, and the ADR-0001-mandated edges that the
// source-less modules have not consumed yet are excluded explicitly, per module, from one committed
// register. The register is guarded by `verifyDependencyAdviceRegister`, so an exclusion cannot
// outlive the code that consumes the edge. Every other piece of advice fails the build.
configure<com.autonomousapps.DependencyAnalysisExtension> {
    val register = file("gradle/dependency-advice-exclusions.txt")
    val excluded = register.readLines()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val parts = trimmed.split("|")
            if (parts.size == 3) parts[0].trim() to parts[1].trim() else null
        }
        .groupBy({ it.first }, { it.second })

    // ADR-0007 names the stable `datastore-preferences` artifact for the Android favourites store; its
    // API lives in the `datastore-core`/`datastore-preferences-core` artifacts it brings. One bundle
    // keeps the declaration on the artifact the ADR names instead of three (`TASK-040`).
    structure {
        bundle("androidx-datastore-preferences") {
            primary("androidx.datastore:datastore-preferences")
            includeGroup("androidx.datastore")
        }
    }

    issues {
        all {
            onAny { severity("fail") }
        }
        excluded.forEach { (modulePath, dependencies) ->
            project(modulePath) {
                onUnusedDependencies { exclude(*dependencies.toTypedArray()) }
            }
        }
    }
}

// The build-logic regression suite is part of the local gate: a rule that fails open while the
// clean repository still passes is exactly what `TASK-091`/`TASK-092` exist to prevent, and a suite
// nobody runs cannot prevent it. The reference is lazy and names no task instance, so the entry
// stays configuration-cache compatible. CI (`TASK-025`) wires the same suite into the active set.
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":convention:test"))
}

/**
 * `TASK-066` (`DEC-041`, `DEC-042`): generate the release notes for the current `VERSION` from the
 * Conventional Commits in the range since the previous `v*` tag (`CONTRIBUTING.md` §3.6), so the
 * release body is derived rather than hand-written and no changelog file exists to drift.
 *
 * It writes to `build/releases/notes.md`; tagging and publishing stay human actions (`DEC-049`), and
 * the workflow guard forbids a release step in CI.
 *
 * The subject list is captured as an `@Input` at configuration time rather than run from a captured
 * `providers.exec` inside the action: a script object in the action makes the task incompatible with
 * the configuration cache, which the whole build enables.
 */
val releaseNotes by tasks.registering {
    group = "documentation"
    description = "TASK-066: the release notes for VERSION, generated from Conventional Commits."
    val versionFile = layout.projectDirectory.file("VERSION")
    val version = providers.fileContents(versionFile).asText.map { it.trim() }
    // The commit subjects the notes are built from. A change to any subject in the range re-runs the
    // task, which is what makes the notes a function of history rather than of a cached list.
    val subjects =
        providers.exec {
            commandLine("bash", "tools/release-notes.sh")
        }.standardOutput.asText

    inputs.property("version", version)
    inputs.property("subjects", subjects)
    val outputFile = layout.buildDirectory.file("releases/notes.md")
    outputs.file(outputFile)
    val capturedVersion = version
    val capturedSubjects = subjects
    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("# Multiverse Explorer ${capturedVersion.get()}\n\n${capturedSubjects.get()}")
        logger.lifecycle("release notes written to ${file.absolutePath}")
    }
}

package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `DEC-081` through the **real** `verifyDependencyAdviceRegister` task (`TASK-105`, `B2-R08`).
 *
 * `GAP-022` recorded that the shipped guard decided the wrong thing four ways: it waited for the
 * module to gain source instead of the edge's own consumer consuming it, derived packages from
 * Gradle path spelling (`…feature.character-detail` against the real `…feature.characterdetail`),
 * ignored test consumption, and treated missing package information as "unused" — the one direction
 * that keeps a stale exclusion alive.
 *
 * These fixtures apply the real plugin to a project that provides its own model inputs, so each
 * rule is exercised by the state the audit reproduced rather than by a hand-built snapshot.
 */
class DependencyAdviceRegisterFunctionalTest {

    private val repositoryRoot: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "build-logic/settings.gradle.kts").isFile }

    /**
     * A fixture project with the register, one module with production source, and one dependency
     * module. `consumes` decides whether the module's source imports the dependency's package.
     */
    private fun fixture(
        entry: String,
        moduleConsumes: Boolean,
        dependencyOwnsSource: Boolean,
        modulePath: String = ":feature:character-detail",
        dependencyPath: String = ":core:domain",
        completedTasks: String = "",
    ): File {
        val dir = kotlin.io.path.createTempDirectory("advice-register").toFile()
        val moduleSegment = modulePath.trimStart(':').replace(':', '/')
        val dependencySegment = dependencyPath.trimStart(':').replace(':', '/')
        // The plugin reads the version catalog at apply time, and `settings.gradle.kts` imports the
        // file, so it must exist before the settings script runs.
        File(dir, "gradle").mkdirs()
        File(dir, "gradle/libs.versions.toml").writeText(
            """
            [versions]
            kotlin = "2.4.20"

            [libraries]
            kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
            """.trimIndent() + "\n",
        )
        File(dir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                includeBuild("${repositoryRoot.resolve("build-logic").invariantSeparatorsPath}")
                repositories { gradlePluginPortal(); mavenCentral() }
            }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "advice-register"
            include("$modulePath", "$dependencyPath")
            """.trimIndent() + "\n",
        )
        File(dir, "gradle.properties").writeText("multiverse.packageRoot=com.example\nmultiverse.completedTasks=$completedTasks\n")
        File(dir, "gradle").mkdirs()
        File(dir, "gradle/dependency-advice-exclusions.txt").writeText("# fixture\n$entry\n")
        File(dir, "build.gradle.kts").writeText("plugins { id(\"multiverse.dependency.policy\") }\n")
        // The dependency module: its package becomes the root a consumer must import.
        File(dir, dependencySegment).mkdirs()
        File(dir, "$dependencySegment/build.gradle.kts").writeText("")
        if (dependencyOwnsSource) {
            val pkg = File(dir, "$dependencySegment/src/commonMain/kotlin/com/example/core/domain")
            pkg.mkdirs()
            File(pkg, "Thing.kt").writeText("package com.example.core.domain\n\nclass Thing\n")
        }
        // The consuming module: the package it declares is what the build derives for it.
        File(dir, moduleSegment).mkdirs()
        // A plain project that declares the project dependency: the guard reads the declared
        // dependency from the build model, so no Kotlin plugin is needed and the fixture stays
        // independent of the KGP toolchain.
        File(dir, "$moduleSegment/build.gradle.kts").writeText(
            """
            configurations.create("implementation")
            dependencies { add("implementation", project("$dependencyPath")) }
            """.trimIndent() + "\n",
        )
        val modulePkg = File(dir, "$moduleSegment/src/commonMain/kotlin/com/example/feature/characterdetail")
        modulePkg.mkdirs()
        val import = if (moduleConsumes) "\nimport com.example.core.domain.Thing\n" else "\n"
        File(modulePkg, "Screen.kt").writeText(
            "package com.example.feature.characterdetail$import\nclass Screen\n",
        )
        return dir
    }

    private fun run(dir: File): String =
        GradleRunner.create()
            .withProjectDir(dir)
            .withArguments("verifyDependencyAdviceRegister", "--no-configuration-cache")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()
            .output

    private fun runPassing(dir: File) {
        GradleRunner.create()
            .withProjectDir(dir)
            .withArguments("verifyDependencyAdviceRegister", "--no-configuration-cache")
            .withPluginClasspath()
            .forwardOutput()
            .build()
    }

    @Test
    fun `an exclusion whose edge the consumer already uses is stale`() {
        val dir = fixture(":feature:character-detail|:core:domain|TASK-036", moduleConsumes = true, dependencyOwnsSource = true)
        val output = run(dir)
        assertTrue(
            output.contains("already consumes") && output.contains(":core:domain"),
            "DEC-081: a consumed edge makes the exclusion stale, and the real (hyphen-stripped) package " +
                "must be what is compared; got $output",
        )
    }

    @Test
    fun `an un-consumed edge with a source-owning dependency is a live exclusion`() {
        val dir = fixture(":feature:character-detail|:core:domain|TASK-036", moduleConsumes = false, dependencyOwnsSource = true)
        runPassing(dir)
    }

    @Test
    fun `a dependency with no source cannot be consumed, so the exclusion is live`() {
        val dir = fixture(":feature:character-detail|:core:domain|TASK-036", moduleConsumes = true, dependencyOwnsSource = false)
        runPassing(dir)
    }

    @Test
    fun `an entry naming a module that does not exist is reported`() {
        val dir = fixture(":feature:no-such-module|:core:domain|TASK-036", moduleConsumes = false, dependencyOwnsSource = true)
        val output = run(dir)
        assertTrue(
            output.contains("is not a module of this build"),
            "DEC-081: a register entry must name a live module; got $output",
        )
    }

    @Test
    fun `a malformed entry is a finding rather than ignored input`() {
        val dir = fixture(":feature:character-detail|:core:domain", moduleConsumes = false, dependencyOwnsSource = true)
        val output = run(dir)
        assertTrue(
            output.contains("does not read") || output.contains("<module>|<dependency>|<task>"),
            "DEC-081: malformed input is a finding; got $output",
        )
    }

    @Test
    fun `an entry whose removal task is already Done is reported`() {
        val dir =
            fixture(
                ":feature:character-detail|:core:domain|TASK-036",
                moduleConsumes = false,
                dependencyOwnsSource = true,
                completedTasks = "TASK-036",
            )
        val output = run(dir)
        assertTrue(
            output.contains("already Done"),
            "DEC-081: a finished task may not remain the owner of a live exclusion; got $output",
        )
    }
}

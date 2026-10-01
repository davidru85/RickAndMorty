package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * `multiverse.dependency.policy` — the repository's dependency policy as build checks
 * (DEC-061, TASK-015).
 *
 * Applied by the **root** build script only. It registers three verification tasks and
 * one aggregate:
 *
 * - `verifyDependencyPins` — `TEST-UNIT-014` (`AC-REQ-NFR-006-1`);
 * - `verifyDependencyRationale` — `TEST-UNIT-013` (`AC-REQ-NFR-002-2`);
 * - `verifyDependencyInventory` — `TEST-UNIT-051` (`AC-REQ-NFR-002-1`);
 * - `verifyDependencyPolicy` — the three above, wired into the root `check`.
 *
 * The scanned file set comes from the **build model**, not from a directory walk
 * (`D-01`): the main build's scripts are the existing settings files plus every
 * project's own build file, and the included build `build-logic/` contributes its
 * scripts and Kotlin sources under a fixed exclusion list. A nested checkout inside the
 * repository therefore cannot appear in the scan, and a project path comes from the
 * build rather than from a directory name.
 *
 * The catalog, the file sets and the project paths are all read at configuration time
 * and passed as `@Input`s, so every task is configuration-cache compatible and executes
 * with no `Project` access. The plugin adds no dependency of its own.
 */
class DependencyPolicyPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        check(target == target.rootProject) {
            "`multiverse.dependency.policy` is applied to the root project only; " +
                "found it on `${target.path}`."
        }

        target.pluginManager.apply("base")

        val catalog = CatalogCapture.capture(target.extensions.getByType<VersionCatalogsExtension>())

        val rootDir = target.rootDir
        val buildLogicDir = rootDir.resolve(BUILD_LOGIC)

        // The main build's scripts, taken from the build model: the settings files and
        // every declared project's own build file. A nested checkout is not a project.
        val mainSettingsFiles = SETTINGS_NAMES.map { rootDir.resolve(it) }.filter { it.isFile }
        val projectBuildFiles = target.allprojects.map { it.buildFile }.filter { it.isFile }
        val mainBuildScripts = target.files(mainSettingsFiles, projectBuildFiles)

        // Build state and IDE output are never sources.
        val buildLogicScripts = target.fileTree(buildLogicDir) {
            include("**/*.gradle.kts", "**/*.gradle")
            exclude(*BUILD_STATE_EXCLUDES)
        }
        val buildLogicSources = target.fileTree(buildLogicDir) {
            include("**/src/**/*.kt", "**/src/**/*.gradle.kts")
            // Test sources are not build logic. They assert the policy's own behaviour and
            // legitimately carry version literals as data, so scanning them would make the
            // policy reject its own regression suite (TASK-091). The build scripts and the
            // production build logic remain fully scanned.
            exclude(*BUILD_STATE_EXCLUDES, TEST_SOURCE_GLOB)
        }
        // The policy package is excluded from the name-lookup rule on purpose:
        // `CatalogCapture` must read the catalog this task verifies.
        val catalogLookupSources = target.fileTree(buildLogicDir) {
            include("**/src/**/*.kt", "**/src/**/*.gradle.kts")
            exclude(*BUILD_STATE_EXCLUDES, POLICY_PACKAGE_GLOB)
        }

        // Root-relative build-file path -> project path, taken from `Project.path` so a
        // `projectDir` remap cannot change a project's identity (F-05).
        val projectPaths = target.allprojects.associate { project ->
            project.buildFile.relativeTo(rootDir).invariantSeparatorsPath to project.path
        }

        // Paths that must not exist. They are declared even while absent, so creating one
        // invalidates a reusable configuration-cache entry (F-03).
        val forbiddenRoots = target.files(
            SETTINGS_NAMES.filter { it.endsWith(".gradle") }.map { rootDir.resolve(it) },
            rootDir.resolve("buildSrc"),
            buildLogicDir.resolve("buildSrc"),
        )

        // Both settings files, for the cross-build catalog check (F-02).
        val settingsFiles = target.files(
            SETTINGS_NAMES.map { rootDir.resolve(it) }.filter { it.isFile },
            buildLogicDir.resolve("settings.gradle.kts"),
        )

        val pins = target.tasks.register<DependencyPinsTask>("verifyDependencyPins") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-014: every external version is an exact pin, the build is Kotlin DSL without " +
                "`buildSrc`, the Gradle wrapper is pinned by checksum, and `VERSION` is the single source for the " +
                "application version (AC-REQ-NFR-006-1, AC-REQ-NFR-006-2; DEC-061, DEC-067)."
            this.catalog.set(catalog)
            catalogFile.set(target.layout.projectDirectory.file("gradle/libs.versions.toml"))
            wrapperProperties.set(target.layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties"))
            versionFile.set(target.layout.projectDirectory.file("VERSION"))
            this.allBuildScripts.from(mainBuildScripts, buildLogicScripts)
            rootDirectory.set(target.layout.projectDirectory)
            this.mainBuildScripts.from(mainBuildScripts)
            this.buildLogicScripts.from(buildLogicScripts)
            this.policySources.from(buildLogicSources)
            this.projectBuildFiles.from(projectBuildFiles)
            this.forbiddenRoots.from(forbiddenRoots)
            this.settingsFiles.from(settingsFiles)
        }

        val rationale = target.tasks.register<DependencyRationaleTask>("verifyDependencyRationale") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-013: every catalog entry has a named rationale, no concern has more than two " +
                "solutions, and the toolchain rows match their sources (AC-REQ-NFR-002-2; DEC-061)."
            this.catalog.set(catalog)
            designDocument.set(target.layout.projectDirectory.file("docs/DESIGN.md"))
            wrapperProperties.set(target.layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties"))
            daemonJvmProperties.set(target.layout.projectDirectory.file("gradle/gradle-daemon-jvm.properties"))
            rootDirectory.set(target.layout.projectDirectory)
        }

        val inventory = target.tasks.register<DependencyInventoryTask>("verifyDependencyInventory") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-051: the README dependency inventory lists exactly the catalog's libraries and " +
                "plugins with their version and declaration state (AC-REQ-NFR-002-1; DEC-061)."
            this.catalog.set(catalog)
            catalogFile.set(target.layout.projectDirectory.file("gradle/libs.versions.toml"))
            readme.set(target.layout.projectDirectory.file("README.md"))
            readmeEs.set(target.layout.projectDirectory.file("README.es.md"))
            this.buildScripts.from(mainBuildScripts, buildLogicScripts)
            this.catalogLookupSources.from(catalogLookupSources)
            this.projectPaths.set(projectPaths)
            rootDirectory.set(target.layout.projectDirectory)
        }

        // TASK-028 (`TEST-UNIT-044`, TESTING.md 14.2): the required-check set must be present and
        // blocking in the workflow configuration. A workflow that omits a suite, or neutralises it
        // with `continue-on-error`, produces a green rollup while proving less than the gate claims.
        val workflowFiles = target.fileTree(rootDir.resolve(".github")) {
            include("workflows/*.yml", "workflows/*.yaml")
        }
        val workflowGate = target.tasks.register<VerifyWorkflowGateTask>("verifyWorkflowGate") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-044: the required-check set is present and blocking in the workflow " +
                "configuration, and every action is pinned by full commit SHA (AC-REQ-NFR-011-1; DEC-037, DEC-054)."
            this.workflows.from(workflowFiles)
            rootDirectory.set(target.layout.projectDirectory)
        }

        // TASK-024 (`TEST-UNIT-024`, TESTING.md 4.1): no test source set outside the scheduled
        // `contract-live` source set may name a live API host. The task scans test sources only —
        // a production source set legitimately names the host — and fails closed.
        val liveTests = target.files(
            target.fileTree(rootDir) {
                // Every Kotlin test source set of every module. The guard itself decides which
                // source set is a test source set and which one is the exempt scheduled job.
                include("**/src/*Test/**/*.kt", "**/src/*test/**/*.kt")
                include("**/src/commonTest/**/*.kt", "**/src/androidHostTest/**/*.kt", "**/src/androidDeviceTest/**/*.kt")
                include("**/src/*TestFixtures/**/*.kt")
                exclude(*BUILD_STATE_EXCLUDES)
            },
        )
        val liveHosts = target.tasks.register<VerifyNoLiveHostsTask>("verifyNoLiveHosts") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-024: no test source set outside `contract-live` names a live API host " +
                "(REQ-NFR-005; TESTING.md 4.1)."
            this.testSources.from(liveTests)
            rootDirectory.set(target.layout.projectDirectory)
        }

        // TASK-029 (`TEST-UNIT-015`, AC-REQ-NFR-007-1): the documented local gate commands must
        // exist and the aggregate must really depend on the checks its row claims. A task cannot
        // inspect another task's graph, and tasks registered later by other plugins are invisible
        // before the build is configured, so both values are captured once, after evaluation
        // (G-03); the hook sets plain values, so the entry stays configuration-cache compatible.
        val documentedGate = target.tasks.register<VerifyDocumentedGateTask>("verifyDocumentedGate") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-015: every documented local gate command exists and the aggregate " +
                "really runs the checks its row claims (AC-REQ-NFR-007-1)."
            this.documents.from(
                target.layout.projectDirectory.file("README.md"),
                target.layout.projectDirectory.file("docs/CONTRIBUTING.md"),
            )
        }
        target.gradle.projectsEvaluated {
            val paths = mutableListOf<String>()
            val names = mutableListOf<String>()
            target.rootProject.allprojects.forEach { project ->
                project.tasks.names.forEach { name ->
                    names += name
                    paths += if (project.path == ":") ":$name" else "${project.path}:$name"
                }
            }
            documentedGate.configure {
                registeredTaskPaths.set(paths.sorted())
                registeredTaskNames.set(names.sorted())
                claimedAggregateDependencies.set(mapOf(":check" to listOf(":verifyDependencyPolicy")))
                // `dependsOn` may hold `TaskProvider`s, so the resolved dependency set is read
                // through `taskDependencies`, not by filtering the raw list (G-03).
                val aggregateTask = target.rootProject.tasks.findByName("check")
                val direct = aggregateTask?.taskDependencies?.getDependencies(aggregateTask)
                    ?.map { it.path }?.sorted() ?: emptyList()
                directDependencies.set(mapOf(":check" to direct))
            }
        }

        // DEC-077: the buildHealth exclusion register may not outlive the placeholder state.
        // TestKit fixtures do not declare the package root, and the register guard does not need
        // it to decide staleness, so it stays optional here.
        val packageRoot = target.findProperty("multiverse.packageRoot")?.toString().orEmpty()
        val adviceRegister = target.tasks.register<VerifyDependencyAdviceRegisterTask>("verifyDependencyAdviceRegister") {
            group = VERIFICATION_GROUP
            description = "DEC-077: every buildHealth exclusion names a live module, a declared " +
                "dependency the module does not yet consume, and the task that removes it."
            this.register.set(target.layout.projectDirectory.file("gradle/dependency-advice-exclusions.txt"))
            this.declaredProjectDependencies.set(
                target.rootProject.allprojects.associate { project ->
                    val declared = project.configurations
                        .mapNotNull { configuration ->
                            runCatching {
                                configuration.dependencies
                                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                                    .map { it.path }
                            }.getOrNull()
                        }
                        .flatten()
                        .toSortedSet()
                        .toList()
                    project.path to declared
                },
            )
            this.packagePrefixes.set(
                target.rootProject.allprojects.associate { project ->
                    val path = project.path
                    val prefix = when {
                        path == ":androidApp" -> "$packageRoot.app"
                        path.startsWith(":core:") -> "$packageRoot.${path.removePrefix(":core:")}"
                        path.startsWith(":feature:") -> "$packageRoot.feature.${path.removePrefix(":feature:")}"
                        else -> null
                    }
                    path to prefix
                }.filterValues { it != null } as Map<String, String>,
            )
            this.productionSources.from(
                target.fileTree(rootDir) {
                    include("**/src/*Main/kotlin/**/*.kt", "**/src/main/kotlin/**/*.kt")
                    exclude(*BUILD_STATE_EXCLUDES)
                },
            )
        }

        val aggregate = target.tasks.register("verifyDependencyPolicy") {
            group = VERIFICATION_GROUP
            description = "Verifies the dependency policy: exact pins, per-entry rationale and README inventory " +
                "(TEST-UNIT-013, TEST-UNIT-014, TEST-UNIT-051; DEC-061)."
            dependsOn(pins, rationale, inventory, liveHosts, workflowGate, documentedGate, adviceRegister)
        }

        target.tasks.named("check").configure { dependsOn(aggregate) }

        // TASK-089 (`GAP-013`): the documented artifact commands must not bypass the single
        // `VERSION` source. Every task that can produce, package or install an Android
        // application artifact depends on the canonical validator, so a malformed `VERSION`
        // fails before a usable artifact exists. The wiring is lazy and configuration-cache
        // compatible: it names the aggregate, never a `Project` or `Task` instance.
        target.allprojects.forEach { project ->
            project.pluginManager.withPlugin("com.android.application") {
                project.tasks.configureEach {
                    if (ANDROID_ARTIFACT_TASKS.matches(name)) {
                        dependsOn(aggregate)
                    }
                }
            }
        }
    }

    private companion object {
        const val VERIFICATION_GROUP = "verification"

        /**
         * The artifact-producing task families of the Android application plugin: assemble,
         * bundle, package, install and their per-variant forms. A name outside these families is
         * not an artifact path and is not wired (`TASK-089`).
         */
        val ANDROID_ARTIFACT_TASKS = Regex(
            "^(assemble|bundle|package|install|connected|uninstall|extract|zipApksFor).*",
        )
        const val BUILD_LOGIC = "build-logic"
        const val POLICY_PACKAGE_GLOB = "**/src/main/kotlin/policy/**"
        const val TEST_SOURCE_GLOB = "**/src/test/**"
        val SETTINGS_NAMES = listOf("settings.gradle.kts", "settings.gradle")
        val BUILD_STATE_EXCLUDES = arrayOf("**/build/**", "**/.gradle/**", "**/.kotlin/**")
    }
}

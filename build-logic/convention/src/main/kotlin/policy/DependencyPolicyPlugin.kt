package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import io.github.davidru85.multiverse.buildlogic.libraryNamespace
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * `multiverse.dependency.policy` — the repository's dependency policy as build checks
 * (DEC-061, TASK-015).
 *
 * Applied by the **root** build script only. It registers the dependency verification tasks and
 * one aggregate:
 *
 * - `verifyDependencyPins` — `TEST-UNIT-014` (`AC-REQ-NFR-006-1`);
 * - `verifyDependencyRationale` — `TEST-UNIT-013` (`AC-REQ-NFR-002-2`);
 * - `verifyDependencyInventory` — `TEST-UNIT-051` (`AC-REQ-NFR-002-1`);
 * - `verifyNoAnalytics` — `TEST-UNIT-034` (`AC-REQ-OBS-003-1`);
 * - `verifyDependencyPolicy` — those and the repository guards, wired into the root `check`.
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
            iosProjectFiles.from(
                target.fileTree(target.layout.projectDirectory.dir(IosAppTripwire.IOS_APP_DIRECTORY)) {
                    include("**/project.pbxproj")
                },
            )
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
                // The scheduled live source set (`TASK-027`, `DEC-074`): the guard itself exempts
                // this directory by name, so it must be in the scanned set — otherwise the
                // exemption would be an omission rather than a decision.
                include("**/src/contractLive/**/*.kt")
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
            // DEC-047: the translated README is the only translation, and its command table must
            // stay aligned with the English one. Both files are inputs so neither can pass alone.
            this.translatedPair.from(
                target.layout.projectDirectory.file("README.md"),
                target.layout.projectDirectory.file("README.es.md"),
            )
        }
        // DEFINITION.md §6, TASK-068: the documentation completeness gate over the whole set. It is a
        // separate task from `verifyDocumentedGate` (which owns the documented *commands* of
        // README/CONTRIBUTING), so each failure names the condition it comes from.
        val documentedCompleteness = target.tasks.register<VerifyDocumentedCompletenessTask>("verifyDocumentedCompleteness") {
            group = VERIFICATION_GROUP
            description =
                "DEFINITION.md §6 documentation completeness: DOC1 headers, DOC2 links, DOC4 " +
                    "identifier uniqueness, DOC6 audit rows and DOC8 placeholders (TASK-068)."
            // The documentation set: every Markdown file under `docs/` plus the three top-level
            // documents. The `docs/` tree is captured as an `@InputFiles` file collection, so a new
            // document enters the gate the moment it exists rather than when someone remembers it.
            val docsDirectory = target.layout.projectDirectory.dir("docs").asFile
            val docsMarkdown =
                docsDirectory
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "md" }
                    .toList()
            this.documents.from(
                docsMarkdown,
                target.layout.projectDirectory.file("README.md"),
                target.layout.projectDirectory.file("README.es.md"),
                target.layout.projectDirectory.file("AGENTS.md"),
            )
            this.rootDirectory.set(target.layout.projectDirectory)
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
                // TASK-103 (`B2-R06`): coverage is decided on the EFFECTIVE graph of the invocation
                // the row documents, not on a direct edge. The graph is read from the task's own
                // `taskDependencies`, which resolves both `dependsOn` TaskProviders and the
                // transitive closure, so a required suite reachable only through an intermediate
                // aggregate is still proved to run.
                selectedGraph.set(
                    mapOf(
                        "allTests" to selectedTaskPaths(target.rootProject, "allTests"),
                        ":check" to selectedTaskPaths(target.rootProject, "check"),
                        "check" to selectedTaskPaths(target.rootProject, "check"),
                        // Every module's own aggregate, so the guard can name the suites a
                        // "shared and unit tests" row must reach.
                        ":core:testing:allTests" to selectedTaskPaths(target.rootProject, ":core:testing:allTests"),
                        ":build-logic:convention:test" to selectedTaskPaths(target.rootProject, ":build-logic:convention:test"),
                    ),
                )
                // The claims the documentation rows make, as data. A row that documents
                // `./gradlew allTests` claims the shared and unit suites; the guard decides whether
                // the invocation honours that claim.
                // `TASK-103` (`B2-R06`): the root `check` depends on the build-logic suite through a
                // lazy `TaskReference` to an INCLUDED build, which `taskDependencies` cannot
                // materialise (it yields no `Task`). The reference is captured by name so the
                // documented row can still be proved to reach it.
                // The build holds at most one such reference today (the build-logic regression
                // suite in the root `check`), and it names the task inside the included build. The
                // name is what a documented token must match, so a renamed task is not accepted.
                includedBuildTaskNames.set(includedBuildReferences(target.rootProject))
                documentedClaims.set(
                    mapOf(
                        ":check" to listOf(":verifyDependencyPolicy"),
                        // The shared suites belong to the module aggregate; the build-logic suite is
                        // reached by its own token in the same invocation, which the union rule
                        // above decides.
                        ":core:testing:allTests" to
                            listOf(":core:testing:testAndroidHostTest", ":core:testing:iosSimulatorArm64Test"),
                    ),
                )
            }
        }

        // DEC-077: the buildHealth exclusion register may not outlive the placeholder state.
        // TestKit fixtures do not declare the package root, and the register guard does not need
        // it to decide staleness, so it stays optional here.
        val packageRoot = target.findProperty("multiverse.packageRoot")?.toString().orEmpty()
        val adviceRegister = target.tasks.register<VerifyDependencyAdviceRegisterTask>("verifyDependencyAdviceRegister") {
            group = VERIFICATION_GROUP
            description = "DEC-081: every buildHealth exclusion names a live module, a declared " +
                "dependency the edge's own consumer does not yet consume, and a removal task that is not Done."
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
            // DEC-081: the package of a module is the one its source declares, read from the build
            // model. Deriving it from the Gradle path is what produced `…feature.character-detail`
            // for a module whose code declares `…feature.characterdetail` (GAP-022).
            this.packageNames.set(
                target.rootProject.allprojects
                    .filter { project -> hasSource(project, rootDir) }
                    .associate { project -> project.path to project.libraryNamespace().get() },
            )
            // The package roots of every dependency the register can name: a project dependency's
            // package is the dependency module's own declared package; an external one's is its
            // group. A dependency with neither is left absent on purpose, so the task reports the
            // missing fact instead of assuming "unused".
            this.dependencyPackageRoots.set(dependencyPackageRoots(target.rootProject, rootDir))
            this.modulesWithSource.set(
                target.rootProject.allprojects
                    .filter { project -> hasSource(project, rootDir) }
                    .map { it.path }
                    .toSet(),
            )
            this.productionSources.from(
                target.fileTree(rootDir) {
                    include("**/src/*Main/kotlin/**/*.kt", "**/src/main/kotlin/**/*.kt")
                    exclude(*BUILD_STATE_EXCLUDES)
                },
            )
            this.testSources.from(
                target.fileTree(rootDir) {
                    include("**/src/*Test/kotlin/**/*.kt", "**/src/test/kotlin/**/*.kt")
                    exclude(*BUILD_STATE_EXCLUDES)
                },
            )
            // The excluded configurations the register's edges use today. `DEC-081` records that
            // test consumption counts, and this is the set that decides "the excluded
            // configuration's own source set" without the build model inside the task.
            this.testConfigurationEdges.set(
                target.rootProject.allprojects.flatMap { project ->
                    project.configurations
                        .filter { configuration ->
                            runCatching { configuration.name.endsWith("TestImplementation") }.getOrDefault(false)
                        }
                        .flatMap { configuration ->
                            runCatching {
                                configuration.dependencies
                                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                                    .map { "${project.path}|${it.path}" }
                            }.getOrDefault(emptyList())
                        }
                }.toSet(),
            )
            // A removal task that has already finished may not remain the owner of a live exclusion.
            // The ids come from the backlog's completed set, which the build reads as a property so
            // the task stays configuration-cache safe.
            this.completedTasks.set(
                (target.findProperty("multiverse.completedTasks")?.toString().orEmpty())
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
            )
        }

        // TASK-047 (`TEST-UNIT-034`, AC-REQ-OBS-003-1): no analytics artifact in the catalog or in the
        // resolved release graph of the shipped Android app. A configuration is resolved only by a
        // task of the project that owns it, so the graph is read by an instance registered in
        // `:androidApp`, whose variant configurations exist once every project is evaluated; the root
        // instance reads the catalog and depends on it.
        val noAnalytics = target.tasks.register<VerifyNoAnalyticsTask>("verifyNoAnalytics") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-034: no analytics, tracking, crash-reporting or telemetry artifact in the catalog " +
                "or the release graph of the shipped Android app (AC-REQ-OBS-003-1; DEC-038)."
            this.catalog.set(catalog)
            requiresShippedGraph.set(false)
        }
        target.gradle.projectsEvaluated {
            val app = target.rootProject.allprojects.firstOrNull { it.path == SHIPPED_APP } ?: return@projectsEvaluated
            val release = app.configurations.findByName(SHIPPED_RELEASE_GRAPH)
            val shipped = app.tasks.register<VerifyNoAnalyticsTask>("verifyNoAnalytics") {
                group = VERIFICATION_GROUP
                description = "TEST-UNIT-034: no analytics artifact in this app's resolved $SHIPPED_RELEASE_GRAPH " +
                    "(AC-REQ-OBS-003-1; DEC-038)."
                requiresShippedGraph.set(true)
                if (release != null) shippedGraph.set(release.incoming.resolutionResult.rootComponent)
            }
            noAnalytics.configure { dependsOn(shipped) }
        }

        // TASK-048 (`TEST-UNIT-027`, `TEST-UNIT-028`, `TEST-UNIT-030`, `TEST-UNIT-031`): the security
        // baseline's four policy checks. Each reads its real source (never a hard-coded key) and
        // fails closed, so a store key, a permission entry, a register row or a reporting route that
        // drifts from the documents fails the build rather than passing quietly.
        val persistedInventory = target.tasks.register<VerifyPersistedFieldInventoryTask>("verifyPersistedFieldInventory") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-027: every persisted field classified in SECURITY.md 3 exists in code and every " +
                "store key is classified; the preference keys match CONTRACTS.md IC-021 (AC-REQ-SEC-003-1)."
            securityDocument.set(target.layout.projectDirectory.file("docs/SECURITY.md"))
            contractsDocument.set(target.layout.projectDirectory.file("docs/CONTRACTS.md"))
            favoritesAndroid.set(target.layout.projectDirectory.file(FAVORITES_ANDROID_SOURCE))
            favoritesApple.set(target.layout.projectDirectory.file(FAVORITES_APPLE_SOURCE))
            settingsAndroid.set(target.layout.projectDirectory.file(SETTINGS_ANDROID_SOURCE))
            settingsApple.set(target.layout.projectDirectory.file(SETTINGS_APPLE_SOURCE))
            shell.set(target.layout.projectDirectory.file(SHELL_SOURCE))
            imageLoader.set(target.layout.projectDirectory.file(IMAGE_LOADER_SOURCE))
            rootDirectory.set(target.layout.projectDirectory)
        }

        val manifests = target.fileTree(rootDir) {
            include("**/AndroidManifest.xml")
            exclude(*BUILD_STATE_EXCLUDES)
        }
        // `iosApp/` does not exist yet: the plist input is declared while empty, so the first
        // `Info.plist` to land is scanned rather than being an untracked file the task happens to see.
        val plists = target.fileTree(rootDir) {
            include("**/Info.plist", "**/*.plist")
            exclude(*BUILD_STATE_EXCLUDES)
        }
        val noMicSpeech = target.tasks.register<VerifyNoMicSpeechPermissionTask>("verifyNoMicSpeechPermission") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-028: neither shipped app declares RECORD_AUDIO or a speech/microphone usage " +
                "description (AC-REQ-SEC-004-1)."
            this.manifests.from(manifests)
            this.plists.from(plists)
            rootDirectory.set(target.layout.projectDirectory)
        }

        val advisoryRegister = target.tasks.register<VerifyAdvisoryRegisterTask>("verifyAdvisoryRegister") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-030: every SECURITY.md 11.3 register row carries the 12 columns of 11.2 with no " +
                "blank or placeholder cell; an empty register passes (AC-REQ-SEC-006-1)."
            securityDocument.set(target.layout.projectDirectory.file("docs/SECURITY.md"))
            rootDirectory.set(target.layout.projectDirectory)
        }

        val reportingRoute = target.tasks.register<VerifyReportingRouteTask>("verifyReportingRoute") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-031: the vulnerability-reporting route in SECURITY.md 10 and CONTRIBUTING.md 10 is " +
                "stated identically (AC-REQ-SEC-007-1)."
            securityDocument.set(target.layout.projectDirectory.file("docs/SECURITY.md"))
            contributingDocument.set(target.layout.projectDirectory.file("docs/CONTRIBUTING.md"))
            rootDirectory.set(target.layout.projectDirectory)
        }

        val aggregate = target.tasks.register("verifyDependencyPolicy") {
            group = VERIFICATION_GROUP
            description = "Verifies the dependency policy and the security baseline: exact pins, per-entry rationale, " +
                "README inventory, no analytics artifact, and the persisted-field, permission, advisory-register and " +
                "reporting-route checks (TEST-UNIT-013/014/051/034, TEST-UNIT-027/028/030/031; DEC-061, TASK-048)."
            dependsOn(
                pins,
                rationale,
                inventory,
                liveHosts,
                workflowGate,
                documentedGate,
                adviceRegister,
                noAnalytics,
                persistedInventory,
                noMicSpeech,
                advisoryRegister,
                reportingRoute,
                // DEFINITION.md §6 (`TASK-068`): the documentation completeness gate. It joins the
                // aggregate so a document that loses its header, a broken link, a duplicated
                // definition, a malformed audit row or a surviving placeholder fails `check` rather
                // than waiting for a reviewer to notice.
                documentedCompleteness,
            )
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
        const val SHIPPED_APP = ":androidApp"
        const val SHIPPED_RELEASE_GRAPH = "releaseRuntimeClasspath"

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

        // The real persisted-field sources the inventory reads (`TASK-048`, `TEST-UNIT-027`).
        const val FAVORITES_ANDROID_SOURCE =
            "core/data/src/androidMain/kotlin/io/github/davidru85/multiverse/core/data/favorites/DataStoreFavoritesLocalDataSource.kt"
        const val FAVORITES_APPLE_SOURCE =
            "core/data/src/iosMain/kotlin/io/github/davidru85/multiverse/core/data/favorites/UserDefaultsFavoritesLocalDataSource.kt"
        const val SETTINGS_ANDROID_SOURCE =
            "core/data/src/androidMain/kotlin/io/github/davidru85/multiverse/core/data/settings/DataStoreAppSettingsLocalDataSource.kt"
        const val SETTINGS_APPLE_SOURCE =
            "core/data/src/iosMain/kotlin/io/github/davidru85/multiverse/core/data/settings/UserDefaultsAppSettingsLocalDataSource.kt"
        const val SHELL_SOURCE = "androidApp/src/main/java/io/github/davidru85/multiverse/app/di/ShellModule.kt"
        const val IMAGE_LOADER_SOURCE = "androidApp/src/main/java/io/github/davidru85/multiverse/app/image/ImageLoader.kt"
        val SETTINGS_NAMES = listOf("settings.gradle.kts", "settings.gradle")
        val BUILD_STATE_EXCLUDES = arrayOf("**/build/**", "**/.gradle/**", "**/.kotlin/**")
    }

    /**
     * Every task path an invocation reaches, transitively.
     *
     * `taskDependencies` is asked for the dependency set of a task **provider**, which resolves
     * `dependsOn` lazily and returns the closure Gradle will execute; filtering a raw `dependsOn`
     * list (the previous approach) sees neither providers nor transitive edges (`TASK-103`).
     */
    private fun selectedTaskPaths(root: Project, name: String): List<String> {
        // A task PATH is one task; a bare NAME selects every registered task of that name in every
        // project — which is why bare `allTests` runs each module's aggregate and `:allTests` does
        // not exist. The graph is the union of those tasks' transitive closures.
        val selected =
            if (name.startsWith(":")) {
                // A task path names one task: `:core:testing:allTests` is the task `allTests` in the
                // project `:core:testing`, found by project path, not by name search.
                val separator = name.lastIndexOf(':')
                val projectPath = if (separator <= 0) ":" else name.substring(0, separator)
                val taskName = name.substring(separator + 1)
                listOfNotNull(root.allprojects.firstOrNull { it.path == projectPath }?.tasks?.findByName(taskName))
            } else {
                // A bare name selects every registered task of that name in every project, which is
                // why bare `allTests` runs each module's aggregate.
                root.allprojects.mapNotNull { it.tasks.findByName(name) }
            }
        return selected
            .flatMap { task -> task.taskDependencies.getDependencies(task).map { it.path } + task.path }
            .distinct()
            .sorted()
    }



    /**
     * The included-build task references a project's tasks carry in their raw `dependsOn`.
     *
     * Gradle keeps `gradle.includedBuild("x").task(":convention:test")` as a lazy
     * `TaskReference`, not as a `Task`, so the resolved closure omits it. The documented gate has
     * to see it: the build-logic suite is one of the shared suites a row claims to run
     * (`TASK-103`, `GAP-020`).
     */
    private fun includedBuildReferences(root: Project): List<String> =
        root.tasks.flatMap { task ->
            task.dependsOn.filterIsInstance<org.gradle.api.tasks.TaskReference>().map { it.name }
        }.distinct()


    /**
     * The Kotlin package a module's own source declares, read from the source tree.
     *
     * The file's `package` declaration is the fact `DESIGN.md` §3.4 fixes; the Gradle path is not.
     * Only the module's own source is read — never a generated directory and never another module.
     */
    private fun declaredPackageOf(project: Project, rootDir: File): String? {
        val segment = project.path.removePrefix(":").replace(':', '/')
        val srcRoot = File(rootDir, "$segment/src")
        if (!srcRoot.isDirectory) return null
        // Every source set counts, in a stable order: a module whose only source today is the
        // scheduled live compilation still declares the package its production code will use, and
        // the module's own package is what its dependencies are compared against (`DEC-081`).
        return srcRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .mapNotNull { file ->
                file.useLines { lines -> lines.firstOrNull { it.trimStart().startsWith("package ") } }
            }
            .firstOrNull()
            ?.removePrefix("package ")
            ?.trim()
            ?.removeSuffix(";")
    }

    /** Whether a module owns any Kotlin source at all, which decides "unknown" versus "not yet". */
    private fun hasSource(project: Project, rootDir: File): Boolean {
        val segment = project.path.removePrefix(":").replace(':', '/')
        return File(rootDir, "$segment/src").walkTopDown().any { it.isFile && it.extension == "kt" }
    }

    /**
     * Dependency path (or external coordinate) to the package roots its artifact exposes.
     *
     * A project dependency's package is the dependency module's own declared package. An external
     * artifact's is its group, which is the vendor's package root. A dependency whose package
     * cannot be established is absent from this map on purpose: the task turns that absence into a
     * diagnostic rather than assuming the edge is unused (`DEC-081`).
     */
    private fun dependencyPackageRoots(root: Project, rootDir: File): Map<String, String> {
        val projects =
            root.allprojects
                .filter { project -> hasSource(project, rootDir) }
                .associate { project -> project.path to project.libraryNamespace().get() }
        val externals =
            root.allprojects
                .flatMap { project ->
                    project.configurations.flatMap { configuration ->
                        runCatching {
                            configuration.dependencies
                                .filterIsInstance<org.gradle.api.artifacts.ExternalModuleDependency>()
                                .map { it.group + ":" + it.name }
                        }.getOrDefault(emptyList())
                    }
                }
                .distinct()
                .associateWith { coordinate -> coordinate.substringBefore(':') }
        return projects + externals
    }

    /** The suites a row documenting "all shared and unit tests" must actually execute. */
    private val SHARED_SUITE_TASKS =
        listOf(
            ":core:testing:testAndroidHostTest",
            ":core:testing:iosSimulatorArm64Test",
            ":build-logic:convention:test",
        )
}

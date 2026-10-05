package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/** DEC-112, TEST-UNIT-061: prove the parallel workers cover the active gate and feed `android`. */
internal object ModularWorkflowGate {
    private val workers = setOf(
        "module-checks", "designsystem", "app-tests", "app-artifacts", "build-logic", "policies", "dependency-health", "contract-replay",
    )
    private val modules = setOf(
        ":core:domain", ":core:data", ":core:presentation", ":core:testing", ":core:diagnostics",
        ":feature:discovery", ":feature:character-detail", ":feature:favorites", ":feature:episodes", ":feature:settings",
    )
    private val requiredCommands = mapOf(
        "designsystem" to setOf(":core:designsystem:check"),
        "app-tests" to setOf(
            ":androidApp:testDebugUnitTest",
            ":androidApp:lint",
            // `TASK-045` (`TEST-UI-012`, `TEST-UI-016`): the screens' committed baselines are verified
            // in the gate, so a drifted or missing baseline fails rather than passing silently.
            ":androidApp:verifyRoborazziDebug",
            ":feature:discovery:verifyRoborazziAndroidHostTest",
            ":feature:character-detail:verifyRoborazziAndroidHostTest",
            ":feature:favorites:verifyRoborazziAndroidHostTest",
            ":feature:settings:verifyRoborazziAndroidHostTest",
        ),
        // `TASK-048` (`TEST-UNIT-028`, the artifact half): the release APK's merged permission table.
        // `TASK-050` (`TEST-UNIT-019`): the release APK and graph are independent of the iOS app.
        // `TASK-125`/`TASK-126` (`PERF-009`, `DEC-152`): the release APK stays within 12 MiB.
        "app-artifacts" to setOf(
            ":androidApp:assembleDebug",
            ":androidApp:verifyReleaseArtifact",
            ":androidApp:verifySdkLevels",
            ":androidApp:verifyShippedPermissions",
            ":androidApp:verifyMilestoneIndependence",
            ":androidApp:verifyReleaseApkSize",
        ),
        "build-logic" to setOf(":build-logic:convention:check"),
        "policies" to setOf("verifyModuleBoundaries", "verifyDependencyPolicy", "verifyRepositoryHygiene", "verifyNoLiveHosts", "verifyWorkflowGate", "verifyDocumentedGate"),
        "dependency-health" to setOf("buildHealth"),
        "contract-replay" to setOf(":core:data:contractTestReplayAndroidHost"),
    )
    private const val MATRIX_RUN = "./gradlew \$GRADLE_TASKS --stacktrace"
    private const val IOS_SCHEME = "MultiverseExplorer"
    private const val RESULT_RUN = "python3 .github/scripts/verify-ci-results.py"

    fun matches(document: WorkflowDocument): Boolean =
        document.jobNames().any { it in workers } || document.jobEntries().any { (name, job) -> name == "android" && job.valueAt("needs") != null }

    fun scan(document: WorkflowDocument, root: File, iosRestored: Boolean): List<WorkflowGateGuard.Finding> {
        val findings = mutableListOf<WorkflowGateGuard.Finding>()
        val jobs = document.jobEntries().toMap()
        val native = iosRestored || "ios" in jobs
        val expected = workers + if (native) setOf("ios") else emptySet()
        fun report(job: JobNode?, reason: String) {
            findings += WorkflowGateGuard.Finding(document.relative(root), job?.lineOf("steps") ?: document.lineOfKey("jobs") ?: 1, reason)
        }
        (expected + "android").forEach { name ->
            val job = jobs[name]
            if (job == null) {
                report(null, "the `$name` job is missing from the modular gate (TEST-UNIT-061)")
                return@forEach
            }
            // `TASK-051`: the iOS runner is `xcode-27`, because `tools/swift-tools.lock` pins
            // swift-format to Xcode 27.0 and `macos-latest` ships Xcode 26.x. The runner image is
            // asserted here as well as in `WorkflowGateGuard`, so a job moved back to a runner
            // without the locked toolchain fails the gate rather than the Swift step.
            val runner = if (name == "ios") "xcode-27" else "ubuntu-latest"
            if (job.stringAt("runs-on") != runner) report(job, "the `$name` job must run on `$runner`")
            val condition = job.scalarAt("if")
            if (name == "android") {
                if (condition?.normalisedCondition() != "always()") report(job, "the `android` result must always run, including after a failed or skipped worker")
            } else {
                if (condition != null) report(job, "the `$name` job carries `if: $condition`, which can skip its required checks")
                if (job.valueAt("needs") != null) report(job, "the `$name` worker has needs; independent checks must start in parallel")
            }
            if (job.valueAt("continue-on-error") != null && job.valueAt("continue-on-error") != false) report(job, "the `$name` job cannot use continue-on-error")
            val checkout = job.steps().filter { it.stringAt("uses")?.startsWith("actions/checkout@") == true }
            if (checkout.size != 1 || checkout.singleOrNull()?.valueAt("with").asMap()["fetch-depth"]?.toString() != "0") {
                report(job, "the `$name` job needs one full-history checkout (fetch-depth: 0)")
            }
            job.steps().forEach { step ->
                if (step.valueAt("continue-on-error") != null && step.valueAt("continue-on-error") != false) {
                    report(job, "a step of `$name` cannot use continue-on-error")
                }
                val protectedEnvironment = when (name) {
                    "module-checks" -> setOf("GRADLE_TASKS")
                    "android" -> setOf("CI_JOB_RESULTS", "CI_REQUIRE_IOS")
                    else -> emptySet()
                }
                if (step.valueAt("env").asMap().keys.any { it in protectedEnvironment }) {
                    report(job, "a step of `$name` cannot override the gate environment")
                }
                if (step.valueAt("if") != null) {
                    val upload = step.stringAt("uses")?.startsWith("actions/upload-artifact@") == true
                    if (!upload || step.scalarAt("if")?.normalisedCondition() != "always()") report(job, "a step of `$name` can skip required verification; only artifact uploads may use if: always()")
                }
            }
        }

        val result = jobs["android"]
        if (result != null) {
            val needs = when (val value = result.valueAt("needs")) {
                is List<*> -> value.filterIsInstance<String>().toSet()
                is String -> setOf(value)
                else -> emptySet()
            }
            if (needs != expected) report(result, "the `android` needs set must include exactly every required worker: ${expected.sorted()}")
            if (result.stringAt("name") != "android") report(result, "the required result must keep the check name `android`")
            val env = result.valueAt("env").asMap()
            if (env["CI_JOB_RESULTS"] != "\${{ toJSON(needs) }}") report(result, "the `android` result must read CI_JOB_RESULTS from toJSON(needs)")
            if (env["CI_REQUIRE_IOS"]?.toString() != native.toString()) report(result, "the `android` result must require the active iOS stage: $native")
            if (result.executableRunTexts() != listOf(RESULT_RUN)) report(result, "the `android` result must execute $RESULT_RUN without replacement or failure suppression")
        }

        jobs["module-checks"]?.let { job ->
            val strategy = job.valueAt("strategy").asMap()
            if (strategy["fail-fast"] != false) report(job, "module-checks must disable fail-fast so one failure does not cancel other checks")
            val matrix = strategy["matrix"].asMap()
            if (matrix.keys != setOf("include")) report(job, "module-checks must use a static include matrix without exclusions or dynamic axes")
            val entries = (matrix["include"] as? List<*>)?.map { it.asMap() }.orEmpty()
            if (entries.map { it["module"] }.toSet() != modules || entries.size != modules.size) report(job, "the module matrix must contain each of the ten KMP modules exactly once")
            if (job.valueAt("env").asMap()["GRADLE_TASKS"] != "\${{ matrix.tasks }}" || job.executableRunTexts() != listOf(MATRIX_RUN)) {
                report(job, "module-checks must execute its matrix tasks via $MATRIX_RUN")
            }
            entries.forEach { entry ->
                val module = entry["module"] as? String ?: return@forEach
                val tasks = entry["tasks"]?.toString().orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val required = setOf("$module:compileAndroidMain", "$module:testAndroidHostTest", "$module:ktlintCheck") +
                    if (module in setOf(":core:domain", ":core:data", ":core:testing")) setOf("$module:jvmTest") else emptySet()
                (required - tasks.toSet()).forEach { report(job, "the module matrix omits `$it`") }
                if (tasks.any { !Regex(":[A-Za-z0-9:_-]+").matches(it) || !it.startsWith("$module:") || it.contains("contractTestReplay") }) {
                    report(job, "the `$module` matrix tasks must be unfiltered local tasks; contract replay belongs to its separate invocation")
                }
            }
        }
        requiredCommands.forEach { (name, commands) ->
            val job = jobs[name] ?: return@forEach
            val tokens = job.executableRunTexts().filter { it.executesGradle() }.flatMap { it.split(Regex("\\s+")) }.toSet()
            (commands - tokens).forEach { report(job, "`$it` is not an executable step of the `$name` gate job") }
        }
        jobs["build-logic"]?.let { job ->
            if ("python3 -m unittest discover -s .github/scripts -p 'test_*.py'" !in job.executableRunTexts()) report(job, "build-logic must execute the result-checker regression suite")
        }
        jobs["contract-replay"]?.let { job ->
            val gradle = job.executableRunTexts().filter { it.executesGradle() }
            if (gradle.size != 1 || gradle.single().split(Regex("\\s+")).any { it.endsWith(":testAndroidHostTest") || it.endsWith(":jvmTest") || it == "check" }) {
                report(job, "contract replay must run separately from the unfiltered data suite")
            }
        }
        jobs["ios"]?.let { job ->
            val commands = job.executableRunTexts().filter { it.executesGradle() }.joinToString(" ")
            listOf("iosSimulatorArm64Test", ":core:data:contractTestReplayIosSimulator", ":core:ios:ktlintCheck").forEach { command ->
                if (!commands.contains(command)) report(job, "`$command` is not an executable step of the ios gate job")
            }
            // TASK-051: the Swift quality gate (`DEC-076`) is part of the iOS row, so removing its
            // step is a gate narrowing rather than a simplification. The step runs the pinned script,
            // which fails closed when a tool is missing.
            if (job.executableRunTexts().none { it.contains("tools/swift-lint.sh") }) {
                report(job, "the iOS job must run the Swift quality gate `tools/swift-lint.sh` (`DEC-076`, `TASK-051`)")
            }
            // `TEST-UNIT-046` proves those scripts fail closed, and no job ran it: the suite guarding
            // the gate was itself unguarded, so a defect in it could only surface as a red `ios` job.
            if (job.executableRunTexts().none { it.contains("tools/swift-tools-test.sh") }) {
                report(job, "the iOS job must run the Swift fail-closed suite `tools/swift-tools-test.sh` (`TEST-UNIT-046`)")
            }
            // `DEFINITION.md` §5 REL2, `AC-REQ-NFR-006-3`: the iOS version derives from `VERSION` through
            // the committed `Version.xcconfig`, and only the check proves the two still agree.
            if (job.executableRunTexts().none { it.contains("tools/ios-version.sh --check") }) {
                report(job, "the iOS job must check the app version against `VERSION` with `tools/ios-version.sh --check` (`DEC-067`, REL2)")
            }
            // `TESTING.md` §14.2: the Swift test target — the state holders, the screens and the committed
            // snapshot baselines — runs on every pull request from `TASK-051`/`TASK-059`. Without this
            // step those cases exist and never execute in the gate.
            if (job.executableRunTexts().none { it.contains("xcodebuild") && it.contains(" test") && it.contains("-scheme $IOS_SCHEME") }) {
                report(job, "the iOS job must build the iOS app and run its test target with `xcodebuild test -scheme $IOS_SCHEME` (`TESTING.md` §14.2, `TASK-051`, `TASK-059`)")
            }
        }
        return findings
    }

    private fun Any?.asMap(): Map<*, *> = this as? Map<*, *> ?: emptyMap<Any, Any>()

    private fun String.normalisedCondition(): String = removePrefix("\${{").removeSuffix("}}").trim()

    private fun String.executesGradle(): Boolean =
        startsWith("./gradlew ") && split(Regex("\\s+")).drop(1).all { token ->
            token in setOf("--stacktrace", "--console=plain") || Regex(":?[A-Za-z0-9][A-Za-z0-9:_-]*").matches(token) && !token.startsWith("-")
        }
}

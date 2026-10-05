package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TEST-UNIT-061: independent checks must cover every module and feed the required result. */
class ModularWorkflowGateTest {
    private val modules = listOf(
        ":core:domain", ":core:data", ":core:presentation", ":core:testing", ":core:diagnostics",
        ":feature:discovery", ":feature:character-detail", ":feature:favorites", ":feature:episodes", ":feature:settings",
    )
    private val commands = linkedMapOf(
        "designsystem" to "./gradlew :core:designsystem:check",
        "app-tests" to
            "./gradlew :androidApp:testDebugUnitTest :androidApp:lint :androidApp:verifyRoborazziDebug " +
            ":feature:discovery:verifyRoborazziAndroidHostTest :feature:character-detail:verifyRoborazziAndroidHostTest " +
            ":feature:favorites:verifyRoborazziAndroidHostTest :feature:settings:verifyRoborazziAndroidHostTest",
        "app-artifacts" to
            "./gradlew :androidApp:assembleDebug :androidApp:verifyReleaseArtifact :androidApp:verifySdkLevels " +
            ":androidApp:verifyShippedPermissions :androidApp:verifyMilestoneIndependence :androidApp:verifyReleaseApkSize",
        "build-logic" to "./gradlew :build-logic:convention:check",
        "policies" to "./gradlew verifyModuleBoundaries verifyDependencyPolicy verifyRepositoryHygiene verifyNoLiveHosts verifyWorkflowGate verifyDocumentedGate",
        "dependency-health" to "./gradlew buildHealth",
        "contract-replay" to "./gradlew :core:data:contractTestReplayAndroidHost",
    )

    private fun configuration(): String = buildString {
        appendLine("name: pull-request")
        appendLine("on: [pull_request, push]")
        appendLine("jobs:")
        appendLine("  module-checks:")
        appendLine("    runs-on: ubuntu-latest")
        appendLine("    strategy:")
        appendLine("      fail-fast: false")
        appendLine("      matrix:")
        appendLine("        include:")
        modules.forEach { module ->
            appendLine("          - module: $module")
            val jvm = if (module in listOf(":core:domain", ":core:data", ":core:testing")) " $module:jvmTest" else ""
            appendLine("            tasks: $module:compileAndroidMain $module:testAndroidHostTest $module:ktlintCheck$jvm")
        }
        appendLine("    env:")
        appendLine("      GRADLE_TASKS: \${{ matrix.tasks }}")
        appendLine("    steps:")
        checkout()
        appendLine("      - run: ./gradlew \$GRADLE_TASKS --stacktrace")
        commands.forEach { (job, command) ->
            appendLine("  $job:")
            appendLine("    runs-on: ubuntu-latest")
            appendLine("    steps:")
            checkout()
            appendLine("      - run: $command")
            if (job == "build-logic") appendLine("      - run: python3 -m unittest discover -s .github/scripts -p 'test_*.py'")
        }
        appendLine("  android:")
        appendLine("    name: android")
        appendLine("    runs-on: ubuntu-latest")
        appendLine("    if: \${{ always() }}")
        appendLine("    needs: [module-checks, ${commands.keys.joinToString(", ")}]")
        appendLine("    env:")
        appendLine("      CI_JOB_RESULTS: \${{ toJSON(needs) }}")
        appendLine("      CI_REQUIRE_IOS: 'false'")
        appendLine("    steps:")
        checkout()
        appendLine("      - run: python3 .github/scripts/verify-ci-results.py")
    }

    private fun StringBuilder.checkout() {
        appendLine("      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1")
        appendLine("        with:")
        appendLine("          fetch-depth: 0")
    }

    private fun reasons(mutate: (String) -> String = { it }): List<String> {
        val root = kotlin.io.path.createTempDirectory("modular-gate").toFile()
        val dir = File(root, WorkflowGateGuard.WORKFLOW_DIRECTORY).apply { mkdirs() }
        val workflow = File(dir, "pull-request.yml").apply { writeText(mutate(configuration())) }
        return WorkflowGateGuard.scan(listOf(workflow), root).map { it.reason }
    }

    @Test
    fun `all independent checks with a fail closed result pass`() {
        assertEquals(emptyList(), reasons())
    }

    @Test
    fun `missing module coverage and JVM coverage are rejected`() {
        listOf(":feature:discovery:testAndroidHostTest", ":core:domain:jvmTest", ":feature:episodes:compileAndroidMain").forEach { task ->
            assertTrue(reasons { it.replace(task, "") }.any { it.contains(task) }, "missing $task must block")
        }
    }

    @Test
    fun `conditional advisory and sequential workers are rejected`() {
        listOf("if: false", "continue-on-error: true", "needs: policies").forEach { setting ->
            assertTrue(reasons { it.replace("  app-tests:\n", "  app-tests:\n    $setting\n") }.isNotEmpty(), setting)
        }
    }

    @Test
    fun `step expressions cannot ignore failure or replace the verified environment`() {
        listOf(
            "./gradlew buildHealth" to "./gradlew buildHealth\n        continue-on-error: \${{ true }}",
            "./gradlew \$GRADLE_TASKS --stacktrace" to "./gradlew \$GRADLE_TASKS --stacktrace\n        env:\n          GRADLE_TASKS: ':core:domain:compileAndroidMain'",
            "python3 .github/scripts/verify-ci-results.py" to "python3 .github/scripts/verify-ci-results.py\n        env:\n          CI_JOB_RESULTS: '{}'",
        ).forEach { (from, to) ->
            assertTrue(reasons { it.replace(from, to) }.isNotEmpty())
        }
    }

    @Test
    fun `a missing worker dependency cannot leave the result green`() {
        assertTrue(reasons { it.replace("needs: [module-checks,", "needs: [") }.any { it.contains("needs") })
    }

    @Test
    fun `the result must always run and read the real dependency results`() {
        listOf("if: \${{ always() }}" to "if: success()", "\${{ toJSON(needs) }}" to "'{}'").forEach { (from, to) ->
            assertTrue(reasons { it.replace(from, to) }.isNotEmpty())
        }
    }

    @Test
    fun `a dropped snapshot verification is rejected`() {
        // TEST-UNIT-061 x TASK-045: the committed baselines are verified in the gate, so removing any
        // one of the invocations must fail the guard rather than silently narrowing the coverage.
        listOf(
            ":core:designsystem:check" to ":core:designsystem:test",
            ":androidApp:verifyRoborazziDebug" to ":androidApp:testDebugUnitTest",
            ":feature:discovery:verifyRoborazziAndroidHostTest" to ":feature:discovery:testAndroidHostTest",
            ":feature:settings:verifyRoborazziAndroidHostTest" to ":feature:settings:testAndroidHostTest",
        ).forEach { (removed, replacement) ->
            val reasons = reasons { it.replace(removed, replacement) }
            assertTrue(reasons.any { it.contains(removed) }, "a dropped `$removed` must block")
        }
    }

    @Test
    fun `a dropped release-artifact verification is rejected`() {
        // TEST-UNIT-061 x TASK-048/TASK-050/TASK-126: the checks that read the real release APK run only
        // where a worker names them, because the gate runs explicit tasks rather than `:androidApp:check`
        // (`DEC-112`). Dropping one must fail the guard rather than leave the check unexecuted; that
        // includes `PERF-009`'s size check, which `TASK-125` added to the worker (`DEC-152`).
        listOf(
            ":androidApp:verifyShippedPermissions",
            ":androidApp:verifyMilestoneIndependence",
            ":androidApp:verifyReleaseApkSize",
        ).forEach { removed ->
            val reasons = reasons { it.replace(" $removed", "") }
            assertTrue(reasons.any { it.contains(removed) }, "a dropped `$removed` must block")
        }
    }

    @Test
    fun `matrix omissions and fail fast cancellation are rejected`() {
        listOf("fail-fast: false" to "fail-fast: true", "        include:" to "        exclude: []\n        include:").forEach { (from, to) ->
            assertTrue(reasons { it.replace(from, to) }.isNotEmpty())
        }
    }

    @Test
    fun `echo dry run exclusion and swallowed failure cannot satisfy a worker`() {
        listOf("echo ./gradlew buildHealth", "./gradlew buildHealth --dry-run", "./gradlew buildHealth -x buildHealth", "./gradlew buildHealth || true", "./gradlew # buildHealth").forEach { form ->
            assertTrue(reasons { it.replace("./gradlew buildHealth", form) }.any { it.contains("buildHealth") }, form)
        }
    }

    @Test
    fun `contract replay cannot filter the full data suite`() {
        assertTrue(reasons { it.replace(":core:data:ktlintCheck", ":core:data:ktlintCheck :core:data:contractTestReplayAndroidHost") }.isNotEmpty())
    }

    @Test
    fun `a shallow worker checkout is rejected`() {
        assertTrue(reasons { it.replace("fetch-depth: 0", "fetch-depth: 1") }.isNotEmpty())
    }
}

package io.github.davidru85.multiverse.buildlogic.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-019` — the Android milestone assembles with the iOS app absent (`REQ-PLAT-004`,
 * `AC-REQ-PLAT-004-1`, `DEC-040`).
 *
 * The rule reads two sources: the release APK's entry names and the app's declared release edges.
 * The conforming inputs pass; each mutation reproduces one regression the rule exists to catch, and
 * each empty input fails closed, because a check that inspected nothing proves nothing.
 */
class MilestoneIndependenceTest {

    private val apkEntries =
        listOf(
            "AndroidManifest.xml",
            "classes.dex",
            "classes2.dex",
            "res/drawable/ic_launcher.xml",
            "META-INF/androidx.compose.material3_material3.version",
            "kotlin/kotlin.kotlin_builtins",
        )

    private val edges = listOf(":core:data", ":core:designsystem", ":core:domain", ":feature:discovery")

    private val iosOnly = listOf(":core:ios")

    private fun scan(entries: List<String> = apkEntries, releaseEdges: List<String> = edges, iosOnlyModules: List<String> = iosOnly) =
        MilestoneIndependencePolicy.scan(entries, releaseEdges, iosOnlyModules)

    @Test
    fun `TEST-UNIT-019 an Android-only artifact and graph pass`() {
        assertEquals(emptyList(), scan().map { it.toString() })
    }

    @Test
    fun `TEST-UNIT-019 an iOS framework inside the release APK is reported`() {
        val findings = scan(entries = apkEntries + "assets/Shared.framework/Info.plist")

        assertEquals(1, findings.size, findings.toString())
        assertTrue(findings.single().reason.contains("Shared.framework"), findings.toString())
    }

    @Test
    fun `TEST-UNIT-019 a Kotlin-Native payload inside the release APK is reported`() {
        val findings = scan(entries = apkEntries + "lib/arm64-v8a/kotlin-native-runtime.so")

        assertEquals(1, findings.size, findings.toString())
    }

    @Test
    fun `TEST-UNIT-019 a release edge to an iOS-only module is reported`() {
        val findings = scan(releaseEdges = edges + ":core:ios")

        assertEquals(1, findings.size, findings.toString())
        assertTrue(findings.single().reason.contains("`:core:ios`"), findings.toString())
    }

    @Test
    fun `TEST-UNIT-019 a module that only shares a prefix with an iOS-only module is not reported`() {
        assertEquals(emptyList(), scan(releaseEdges = edges + ":core:iosbridge").map { it.toString() })
    }

    @Test
    fun `TEST-UNIT-019 an empty artifact, edge set or iOS-only set fails closed`() {
        listOf(
            scan(entries = emptyList()),
            scan(releaseEdges = emptyList()),
            scan(iosOnlyModules = emptyList()),
        ).forEach { findings ->
            assertTrue(findings.any { it.reason.contains("fails closed") }, findings.toString())
        }
    }
}

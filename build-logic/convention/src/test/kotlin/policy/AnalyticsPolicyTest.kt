package io.github.davidru85.multiverse.buildlogic.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-034` — no analytics, tracking, advertising, crash-reporting or telemetry artifact in the
 * dependency graph (`REQ-OBS-003`, `AC-REQ-OBS-003-1`, `DEC-038`, `OBSERVABILITY.md` §1).
 *
 * The policy reads two graphs: the catalog — the only place a dependency or plugin may be declared
 * (`P4`, `TEST-UNIT-014`) — and the resolved release runtime graph of the shipped Android app, where
 * a transitive SDK would surface. A conforming control passes, and each mutation fails naming the
 * coordinate and the graph it was found in.
 */
class AnalyticsPolicyTest {
    private val none = VersionConstraint("1.0.0", "", "", emptyList())

    private fun library(coordinates: String) =
        CatalogLibrary(coordinates.substringAfter(':'), coordinates.substringBefore(':'), coordinates.substringAfter(':'), none)

    private fun catalog(
        libraries: List<String> = emptyList(),
        plugins: List<String> = emptyList(),
    ) = CatalogSnapshot(
        libraries = libraries.map(::library),
        plugins = plugins.map { CatalogPlugin(it, it, none) },
        versions = emptyList(),
        bundles = emptyList(),
        catalogNames = listOf("libs"),
    )

    /** The shape of this repository's catalog and shipped graph: the conforming control. */
    private val control =
        catalog(
            libraries =
                listOf(
                    "io.ktor:ktor-client-core",
                    "org.jetbrains.kotlinx:kotlinx-coroutines-core",
                    "androidx.compose.material3:material3",
                    "com.google.android.material:material",
                ),
            plugins = listOf("com.android.application", "org.jetbrains.kotlin.multiplatform", "com.autonomousapps.dependency-analysis"),
        )

    @Test
    fun `TEST-UNIT-034 the conforming catalog and release graph pass`() {
        val violations =
            AnalyticsPolicy.scan(
                control,
                shippedGraph = listOf("io.ktor:ktor-client-okhttp", "com.squareup.okhttp3:okhttp", "com.google.android.gms:play-services-base"),
            )

        assertEquals(emptyList(), violations.map { it.toString() })
    }

    @Test
    fun `TEST-UNIT-034 an analytics library in the catalog fails and is named`() {
        listOf(
            "com.google.firebase:firebase-analytics",
            "com.google.android.gms:play-services-measurement-api",
            "io.sentry:sentry-android",
            "com.mixpanel.android:mixpanel-android",
            "com.segment.analytics.kotlin:android",
            "com.datadoghq:dd-sdk-android-logs",
        ).forEach { coordinates ->
            val violations = AnalyticsPolicy.scan(catalog(libraries = listOf(coordinates)), shippedGraph = emptyList())

            assertEquals(1, violations.size, "$coordinates is an analytics or telemetry artifact")
            assertEquals("TEST-UNIT-034", violations.single().testId)
            assertTrue(violations.single().location.contains(coordinates), violations.single().toString())
        }
    }

    @Test
    fun `TEST-UNIT-034 an analytics Gradle plugin in the catalog fails`() {
        listOf("com.google.gms.google-services", "com.google.firebase.crashlytics", "io.sentry.android.gradle").forEach { id ->
            val violations = AnalyticsPolicy.scan(catalog(plugins = listOf(id)), shippedGraph = emptyList())

            assertEquals(1, violations.size, "$id wires an analytics SDK")
            assertTrue(violations.single().location.contains(id), violations.single().toString())
        }
    }

    @Test
    fun `TEST-UNIT-034 a transitive analytics artifact in the shipped release graph fails`() {
        val violations =
            AnalyticsPolicy.scan(
                control,
                shippedGraph = listOf("io.ktor:ktor-client-core", "com.google.firebase:firebase-crashlytics"),
            )

        assertEquals(1, violations.size, "a declared library can still bring an SDK in transitively")
        assertTrue(violations.single().location.contains("releaseRuntimeClasspath"), violations.single().toString())
        assertTrue(violations.single().location.contains("com.google.firebase:firebase-crashlytics"), violations.single().toString())
    }
}

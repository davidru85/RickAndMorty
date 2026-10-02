package io.github.davidru85.multiverse.buildlogic.policy

/**
 * `TEST-UNIT-034` — no analytics, tracking, advertising, crash-reporting or telemetry artifact in the
 * dependency graph (`REQ-OBS-003`, `AC-REQ-OBS-003-1`, `DEC-038`, `OBSERVABILITY.md` §1).
 *
 * Two graphs are read. The catalog is where every library and plugin is declared (`P4` of
 * `TEST-UNIT-014` rejects a version anywhere else), so a declared SDK is caught at its source. The
 * resolved release runtime graph of the shipped Android app is where a transitive SDK would surface,
 * so a library that brings one in is caught too. The deny-list names the vendors of such SDKs; it is
 * a list, not a proof, and the review rule of `OBSERVABILITY.md` §1 still applies to anything new.
 */
internal object AnalyticsPolicy {
    const val TEST_ID = "TEST-UNIT-034"

    /** Where a transitive finding was resolved: the graph the shipped Android artifact is built from. */
    const val SHIPPED_GRAPH = ":androidApp releaseRuntimeClasspath"

    private const val CATALOG = "gradle/libs.versions.toml"

    /** No entries: what the instance that reads only the shipped graph scans as its catalog. */
    val EMPTY_CATALOG = CatalogSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    /** Maven groups whose every artifact is such an SDK; a sub-group is denied with its parent. */
    private val DENIED_GROUPS: Map<String, String> =
        mapOf(
            "com.google.firebase" to "Firebase analytics, crash reporting and performance",
            "com.crashlytics.sdk.android" to "Crashlytics",
            "io.sentry" to "Sentry",
            "com.bugsnag" to "Bugsnag",
            "com.datadoghq" to "Datadog",
            "com.newrelic.agent.android" to "New Relic",
            "com.mixpanel.android" to "Mixpanel",
            "com.amplitude" to "Amplitude",
            "com.segment.analytics" to "Segment",
            "com.appsflyer" to "AppsFlyer",
            "com.adjust.sdk" to "Adjust",
            "io.branch.sdk.android" to "Branch",
            "com.flurry.android" to "Flurry",
            "com.facebook.android" to "the Meta app-events SDK",
            "com.microsoft.appcenter" to "App Center",
            "com.instabug.library" to "Instabug",
            "com.posthog" to "PostHog",
            "io.embrace" to "Embrace",
            "com.heapanalytics.android" to "Heap",
            "io.opentelemetry" to "OpenTelemetry",
        )

    /** Google Play services is a shared group; only these artifact families are analytics or ads. */
    private const val PLAY_SERVICES = "com.google.android.gms"
    private val DENIED_PLAY_SERVICES =
        listOf("play-services-analytics", "play-services-measurement", "play-services-ads", "play-services-tagmanager")

    /** Gradle plugins that exist to wire one of those SDKs into a build. */
    private val DENIED_PLUGINS =
        listOf("com.google.gms.google-services", "com.google.firebase", "io.sentry", "com.bugsnag", "com.datadoghq", "com.newrelic")

    /** Every finding in [catalog] and in [shippedGraph], whose entries are `group:name` coordinates. */
    fun scan(
        catalog: CatalogSnapshot,
        shippedGraph: List<String>,
    ): List<Violation> =
        buildList {
            catalog.libraries.forEach { library ->
                vendorOf(library.group, library.name)?.let { vendor ->
                    add(Violation(TEST_ID, "$CATALOG: ${library.coordinates}", reason(vendor)))
                }
            }
            catalog.plugins.forEach { plugin ->
                pluginVendorOf(plugin.pluginId)?.let {
                    add(Violation(TEST_ID, "$CATALOG: plugin ${plugin.pluginId}", reason("a plugin that wires $it")))
                }
            }
            shippedGraph.distinct().sorted().forEach { coordinates ->
                vendorOf(coordinates.substringBefore(':'), coordinates.substringAfter(':'))?.let { vendor ->
                    add(Violation(TEST_ID, "$SHIPPED_GRAPH: $coordinates", reason("$vendor, reached transitively")))
                }
            }
        }

    private fun vendorOf(
        group: String,
        name: String,
    ): String? =
        DENIED_GROUPS.entries.firstOrNull { (denied, _) -> group == denied || group.startsWith("$denied.") }?.value
            ?: "Google Play services analytics or ads".takeIf {
                group == PLAY_SERVICES && DENIED_PLAY_SERVICES.any { name == it || name.startsWith("$it-") }
            }

    private fun pluginVendorOf(id: String): String? =
        DENIED_PLUGINS.firstOrNull { id == it || id.startsWith("$it.") }

    private fun reason(vendor: String) =
        "$vendor is an analytics, tracking, advertising, crash-reporting or telemetry artifact; none may be included " +
            "(REQ-OBS-003, DEC-038, OBSERVABILITY.md 1)"
}

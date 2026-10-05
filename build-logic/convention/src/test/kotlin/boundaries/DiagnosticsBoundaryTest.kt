package io.github.davidru85.multiverse.buildlogic.boundaries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-033` — the debug-only diagnostic API is physically absent from release (`REQ-OBS-002`,
 * `AC-REQ-OBS-002-1`, `DEC-088`, ADR-0013) — plus the `TEST-UNIT-017`/`TEST-UNIT-043` rules that
 * admit `:core:diagnostics` to the module graph (`R11`, `R13`, `R16`, `R18`).
 *
 * The exclusion is a property of the graph, not of a runtime flag: `:androidApp` may declare the
 * module from a `debug*` configuration only, and no release-relevant path from the shell — direct,
 * inherited or through another module — may reach it. The fixtures are plain snapshots, so each
 * case is a pure function of its inputs.
 */
class DiagnosticsBoundaryTest {
    private val diagnostics = ":core:diagnostics"
    private val app = ":androidApp"

    private fun project(
        path: String,
        edges: List<DeclaredEdge> = emptyList(),
    ) = ProjectSnapshot(
        path = path,
        directory = path.trimStart(':').replace(':', '/'),
        moduleKind = moduleKindOf(path),
        edges = edges,
        externalDependencies = emptyList(),
        targets = if (path == app) emptyList() else listOf("android", "iosArm64", "iosSimulatorArm64"),
    )

    private fun edge(
        consumer: String,
        producer: String,
        configuration: String = "commonMainImplementation",
        sourceSet: String = "commonMain",
        kind: SourceSetKind = SourceSetKind.PRODUCTION,
        origin: String = configuration,
    ) = DeclaredEdge(consumer, configuration, sourceSet, kind, producer, origin)

    /** An edge of the Android shell, whose dependency blocks are Android production configurations. */
    private fun appEdge(
        producer: String,
        configuration: String,
        origin: String = configuration,
    ) = edge(app, producer, configuration, configuration.removeSuffix("Implementation").ifEmpty { "implementation" }, SourceSetKind.ANDROID_UI, origin)

    /** The accepted topology with `:core:diagnostics`, so only the case under test can contribute. */
    private fun complete(vararg extra: ProjectSnapshot): ModuleGraphSnapshot {
        val leaves =
            (ModuleSet.CONTAINERS + ModuleSet.REQUIRED + diagnostics)
                .filterNot { path -> extra.any { it.path == path } }
                .map { project(it) }
        return ModuleGraphSnapshot((leaves + extra.toList()).sortedBy { it.path })
    }

    /** The graph rules only: the structure rules (`S*`) read feature sources, which these fixtures do not carry. */
    private fun evaluate(snapshot: ModuleGraphSnapshot) =
        ModuleBoundaryRules.evaluate(snapshot, emptyMap()).all().filterNot { it.ruleId.startsWith("S") }

    private fun rule(
        violations: List<ModuleBoundaryViolation>,
        id: String,
    ) = violations.filter { it.ruleId == id }

    @Test
    fun `TEST-UNIT-043 R16 requires the diagnostics module and R13 knows it`() {
        val present = evaluate(complete())
        assertEquals(emptyList(), present.map { it.render() }, "the module set with :core:diagnostics is accepted (DEC-088)")

        val without =
            ModuleGraphSnapshot(
                (ModuleSet.CONTAINERS + ModuleSet.REQUIRED - diagnostics).sorted().map { project(it) },
            )
        val missing = rule(evaluate(without), "R16")
        assertEquals(1, missing.size, "a build without :core:diagnostics fails the topology rule")
        assertTrue(missing.single().reason.contains("`$diagnostics`"), missing.single().reason)
    }

    @Test
    fun `TEST-UNIT-033 the app shell links the diagnostic module from a debug configuration only`() {
        val debug = evaluate(complete(project(app, listOf(appEdge(diagnostics, "debugImplementation")))))
        assertEquals(emptyList(), debug.map { it.render() }, "debugImplementation is the one admitted edge (DEC-088)")

        listOf("implementation", "releaseImplementation", "runtimeOnly", "compileOnly", "api").forEach { configuration ->
            val release = rule(evaluate(complete(project(app, listOf(appEdge(diagnostics, configuration))))), "R11")
            assertTrue(release.isNotEmpty(), "`$configuration` puts the diagnostic API in the release graph")
            assertTrue(release.all { it.producer == diagnostics }, release.map { it.render() }.toString())
        }
    }

    @Test
    fun `TEST-UNIT-033 a diagnostics edge inherited into a release configuration is rejected and names its origin`() {
        val inherited =
            rule(
                evaluate(complete(project(app, listOf(appEdge(diagnostics, "implementation", origin = "diagnosticsBundle"))))),
                "R11",
            )

        assertTrue(inherited.isNotEmpty(), "an inherited edge is an edge of the configuration it reaches (GAP-012)")
        assertTrue(inherited.any { it.originConfiguration == "diagnosticsBundle" }, inherited.map { it.render() }.toString())
    }

    @Test
    fun `TEST-UNIT-033 the diagnostic module never reaches the release graph through another module`() {
        listOf(":feature:discovery", ":core:data", ":core:presentation").forEach { carrier ->
            val violations =
                evaluate(
                    complete(
                        project(app, listOf(appEdge(carrier, "implementation"))),
                        project(carrier, listOf(edge(carrier, diagnostics))),
                    ),
                )
            val closure = rule(violations, "R11").filter { it.producer == diagnostics }

            assertEquals(1, closure.size, "the release closure of :androidApp reaches it through $carrier: $violations")
            assertTrue(closure.single().reason.contains(carrier), closure.single().reason)
        }
    }

    @Test
    fun `TEST-UNIT-033 a debug-only path is not part of the release closure`() {
        val violations =
            evaluate(
                complete(
                    project(app, listOf(appEdge(":feature:discovery", "implementation"))),
                    project(
                        ":feature:discovery",
                        listOf(edge(":feature:discovery", ":core:testing", "commonTestImplementation", "commonTest", SourceSetKind.TEST)),
                    ),
                    project(":core:testing", listOf(edge(":core:testing", ":core:domain"))),
                ),
            )

        assertEquals(emptyList(), rule(violations, "R11").map { it.render() }, "test edges are not shipped")
    }

    @Test
    fun `TEST-UNIT-017 R18 keeps the diagnostic module on the domain contract`() {
        val allowed =
            evaluate(
                complete(
                    project(
                        diagnostics,
                        listOf(
                            edge(diagnostics, ":core:domain", "commonMainApi"),
                            edge(diagnostics, ":core:testing", "commonTestImplementation", "commonTest", SourceSetKind.TEST),
                            edge(diagnostics, ":core:data", "commonTestImplementation", "commonTest", SourceSetKind.TEST),
                        ),
                    ),
                ),
            )
        assertEquals(emptyList(), allowed.map { it.render() }, "production on the contract; tests on the real path (DEC-088)")

        listOf(":core:data", ":core:presentation", ":feature:discovery").forEach { producer ->
            val rejected = rule(evaluate(complete(project(diagnostics, listOf(edge(diagnostics, producer))))), "R18")
            assertEquals(1, rejected.size, "production :core:diagnostics may not depend on $producer")
        }
    }

    @Test
    fun `TEST-UNIT-017 no production consumer other than the debug shell and the iOS framework may declare the diagnostic module`() {
        listOf(
            ":core:data" to "R2",
            ":core:presentation" to "R3",
            ":feature:discovery" to "R8",
        ).forEach { (consumer, id) ->
            val violations = evaluate(complete(project(consumer, listOf(edge(consumer, diagnostics)))))
            assertTrue(rule(violations, id).any { it.producer == diagnostics }, "$consumer -> $diagnostics fails $id: $violations")
        }
    }
}

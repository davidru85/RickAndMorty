package io.github.davidru85.multiverse.buildlogic.boundaries

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-043` R16 — the positive half of the module graph (`GAP-014`, `TASK-091`).
 *
 * Every other rule is a prohibition: it rejects an edge, a dependency or a source file. A build
 * that has silently lost a module has less to prohibit, so before `R16` the check certified a
 * repository whose accepted topology no longer existed. These tests pin both directions — the
 * accepted set passes, and a single missing leaf fails with its own diagnostic — plus the boundary
 * between an accepted feature and an unrecognised `:feature:*` path.
 */
class ModuleTopologyTest {

    private fun snapshot(vararg paths: String) = ModuleGraphSnapshot(
        paths.map { path ->
            ProjectSnapshot(
                path = path,
                directory = path.trimStart(':').replace(':', '/'),
                moduleKind = moduleKindOf(path),
                edges = emptyList(),
                externalDependencies = emptyList(),
            )
        },
    )

    /** The full required set plus the containers, exactly as the real build declares it. */
    private fun completeSnapshot() =
        snapshot(*(ModuleSet.CONTAINERS + ModuleSet.REQUIRED).sorted().toTypedArray())

    private fun rules(log: BoundaryViolationLog, rule: String): List<ModuleBoundaryViolation> =
        log.all().filter { it.ruleId == rule }

    @Test
    fun `the accepted topology produces no graph or topology violation`() {
        val log = ModuleBoundaryRules.evaluate(completeSnapshot(), emptyMap())
        val graph = log.all().filter { it.ruleId == "R16" || it.ruleId == "R13" }
        assertEquals(emptyList(), graph.map { it.render() })
    }

    @Test
    fun `every required leaf is enforced by R16 when absent`() {
        ModuleSet.REQUIRED.sorted().forEach { missing ->
            val without = (ModuleSet.CONTAINERS + ModuleSet.REQUIRED).filterNot { it == missing }
            val log = ModuleBoundaryRules.evaluate(snapshot(*without.sorted().toTypedArray()), emptyMap())
            val messages = rules(log, "R16").map { it.reason }
            assertEquals(1, messages.size, "expected exactly one R16 violation for a missing $missing")
            assertTrue(
                messages.single().contains("`$missing`"),
                "the diagnostic must name the missing module: ${messages.single()}",
            )
        }
    }

    @Test
    fun `a missing feature is a topology failure, not an unknown module`() {
        val without = (ModuleSet.CONTAINERS + ModuleSet.REQUIRED).filterNot { it == ":feature:settings" }
        val log = ModuleBoundaryRules.evaluate(snapshot(*without.sorted().toTypedArray()), emptyMap())
        assertEquals(1, rules(log, "R16").size, "the missing feature fails the topology rule once")
        assertEquals(emptyList(), rules(log, "R13").map { it.render() }, "an absent module is not an unknown module")
    }

    @Test
    fun `containers are allowed and are not counted as required leaves`() {
        val log = ModuleBoundaryRules.evaluate(snapshot(*ModuleSet.CONTAINERS.sorted().toTypedArray()), emptyMap())
        assertEquals(emptyList(), rules(log, "R13").map { it.render() }, "the root and the groupings are containers")
        assertEquals(
            ModuleSet.REQUIRED.size,
            rules(log, "R16").size,
            "every required leaf is reported once when only containers exist",
        )
    }

    @Test
    fun `an invented feature path is unknown rather than an accepted feature`() {
        assertEquals(ModuleKind.UNKNOWN, moduleKindOf(":feature:invented"))
        val log = ModuleBoundaryRules.evaluate(
            snapshot(*(ModuleSet.CONTAINERS + ModuleSet.REQUIRED + ":feature:invented").sorted().toTypedArray()),
            emptyMap(),
        )
        assertEquals(listOf(":feature:invented"), rules(log, "R13").map { it.consumer })
        assertEquals(emptyList(), rules(log, "R16").map { it.render() }, "the required set is still complete")
    }

    @Test
    fun `a planned module is legal before its task`() {
        assertTrue(ModuleSet.PLANNED.all { !ModuleSet.isRequired(it) }, "a planned module is not required yet")
        val withIos = (ModuleSet.CONTAINERS + ModuleSet.REQUIRED + ModuleSet.PLANNED).sorted().toTypedArray()
        val log = ModuleBoundaryRules.evaluate(snapshot(*withIos), emptyMap())
        assertEquals(emptyList(), rules(log, "R16").map { it.render() })
        assertEquals(ModuleKind.CORE_IOS, moduleKindOf(":core:ios"), "a present planned module is classified")
    }

    @Test
    fun `only the accepted feature paths classify as features`() {
        ModuleSet.FEATURES.forEach { path ->
            assertEquals(ModuleKind.FEATURE, moduleKindOf(path), "$path is an accepted feature")
        }
        listOf(":feature:locations", ":feature:invented", ":feature:", ":services:shared").forEach { path ->
            assertEquals(ModuleKind.UNKNOWN, moduleKindOf(path), "$path is not an accepted module")
        }
    }

    @Test
    fun `the required set matches the documented eleven leaves`() {
        assertEquals(11, ModuleSet.REQUIRED.size, "ADR-0001 as amended fixes eleven leaf modules")
        assertEquals(5, ModuleSet.FEATURES.size, "ADR-0010 leaves five feature modules")
    }
}

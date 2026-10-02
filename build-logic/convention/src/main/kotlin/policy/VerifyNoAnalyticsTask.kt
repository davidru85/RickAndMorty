package io.github.davidru85.multiverse.buildlogic.policy

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * `TEST-UNIT-034` — the catalog and the resolved release graph of the shipped Android app contain no
 * analytics artifact (`AC-REQ-OBS-003-1`); the rules are [AnalyticsPolicy]'s.
 *
 * One instance per graph: the root's reads the catalog, and the one registered in `:androidApp` reads
 * that project's own release graph — a configuration is resolved only by a task of the project that
 * owns it. The graph is an input as a resolution result, so it is resolved when the task runs and the
 * task stays configuration-cache compatible. An instance that requires the graph and cannot read it
 * fails rather than passing on the catalog alone.
 */
@DisableCachingByDefault(because = "verification task with no outputs")
abstract class VerifyNoAnalyticsTask : DefaultTask() {
    /** The catalog, read by the root instance. */
    @get:Input
    @get:Optional
    abstract val catalog: Property<CatalogSnapshot>

    /** The resolved `releaseRuntimeClasspath` of `:androidApp`, read by that project's instance. */
    @get:Input
    @get:Optional
    abstract val shippedGraph: Property<ResolvedComponentResult>

    /** Whether this instance must read [shippedGraph]; it fails closed when the graph is absent. */
    @get:Input
    abstract val requiresShippedGraph: Property<Boolean>

    @TaskAction
    fun verify() {
        if (requiresShippedGraph.get() && !shippedGraph.isPresent) {
            throw GradleException(
                "${AnalyticsPolicy.TEST_ID}: `${AnalyticsPolicy.SHIPPED_GRAPH}` could not be read; the release graph is " +
                    "required evidence, so the check fails closed (AC-REQ-OBS-003-1)",
            )
        }
        val shipped = shippedGraph.orNull?.let(::modules).orEmpty()
        val scanned = catalog.orNull ?: AnalyticsPolicy.EMPTY_CATALOG
        val violations = AnalyticsPolicy.scan(scanned, shipped)
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("The analytics policy failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
        logger.lifecycle(
            "$name passed: ${scanned.libraries.size} catalog libraries, ${scanned.plugins.size} plugins and " +
                "${shipped.size} resolved modules checked (${AnalyticsPolicy.TEST_ID}).",
        )
    }

    /** Every external module in the resolved graph, as `group:name`. */
    private fun modules(root: ResolvedComponentResult): List<String> {
        val seen = mutableSetOf<ResolvedComponentResult>()
        val found = sortedSetOf<String>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component)) continue
            (component.id as? ModuleComponentIdentifier)?.let { found += "${it.group}:${it.module}" }
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue.add(it.selected) }
        }
        return found.toList()
    }
}

package io.github.davidru85.multiverse.buildlogic.boundaries

import java.io.Serializable

/**
 * One boundary violation. `originConfiguration` is rendered only when it differs from the
 * effective configuration, so an inherited edge names both the classpath it reaches and the
 * declaration that introduced it (`GAP-012`).
 *
 * The test ids the module-boundary check implements (`TESTING.md` §3.3, §17; `DEC-068`).
 *
 * - [GRAPH] owns the module-graph rules — project edges, source-set kinds and the fail-closed
 *   rule for an unrecognised module.
 * - [DOMAIN_PURITY] owns the external-library rule for `:core:domain`, which is the half of
 *   the purity assertion the Gradle model can decide (`DEC-066`).
 * - [STRUCTURE] owns the staged destination/package rules that `DEC-068` assigns to `TASK-017`.
 * - [TOPOLOGY] owns the positive half: the required leaf modules of ADR-0001 are present (`GAP-014`).
 */
internal object BoundaryTestIds {
    const val GRAPH = "TEST-UNIT-017"
    const val DOMAIN_PURITY = "TEST-UNIT-012"
    const val STRUCTURE = "TEST-UNIT-043"
    const val TOPOLOGY = "TEST-UNIT-043"
}

/**
 * One boundary violation. Everything rendered is repository-relative or an identifier: the task
 * never prints an absolute machine path.
 *
 * @param testId the `TEST-###` id that owns the rule, so a failure line is greppable.
 * @param ruleId the stable rule id inside this check (`R1`…`R15`, `S1`…`S3`).
 * @param consumer the consuming project path, or a project path for a structure rule.
 * @param configuration the Gradle configuration that declared the edge, empty for structure rules.
 * @param sourceSet the classified source set, empty when the rule is not source-set scoped.
 * @param producer the declared project dependency, empty for structure rules.
 * @param reason a one-line explanation naming the rule and the document that owns it.
 */
data class ModuleBoundaryViolation(
    val testId: String,
    val ruleId: String,
    val consumer: String,
    val configuration: String,
    val sourceSet: String,
    val producer: String,
    /** The configuration that declared the dependency, when it differs from the effective one. */
    val originConfiguration: String = "",
    val reason: String,
) : Serializable {

    /** `TEST-UNIT-017 R7 consumer=… configuration=… sourceSet=… producer=…: reason`. */
    fun render(): String = buildString {
        append(testId).append(' ').append(ruleId).append(" consumer=").append(consumer)
        if (configuration.isNotEmpty()) append(" configuration=").append(configuration)
        if (sourceSet.isNotEmpty()) append(" sourceSet=").append(sourceSet)
        if (producer.isNotEmpty()) append(" producer=").append(producer)
        if (originConfiguration.isNotEmpty() && originConfiguration != configuration) {
            append(" origin=").append(originConfiguration)
        }
        append(": ").append(reason)
    }
}

/**
 * An accumulated set of violations. Every rule runs before the task fails, and the rendering is
 * sorted, so a run reports the whole set in a stable order and two identical trees produce
 * identical output.
 */
internal class BoundaryViolationLog {

    private val violations = mutableListOf<ModuleBoundaryViolation>()

    fun add(violation: ModuleBoundaryViolation) {
        violations += violation
    }

    fun isEmpty(): Boolean = violations.isEmpty()

    fun size(): Int = violations.size

    /** The accumulated violations, for a test or a caller that needs the structured form. */
    fun all(): List<ModuleBoundaryViolation> = violations.toList()

    fun render(): String = buildString {
        appendLine("verifyModuleBoundaries failed with ${violations.size} violation(s):")
        violations
            .sortedWith(
                compareBy(
                    { it.testId },
                    { it.ruleId },
                    { it.consumer },
                    { it.configuration },
                    { it.producer },
                    { it.reason },
                ),
            )
            .forEach { appendLine(it.render()) }
    }
}

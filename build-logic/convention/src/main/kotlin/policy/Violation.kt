package io.github.davidru85.multiverse.buildlogic.policy

import java.io.Serializable

/**
 * A collected policy violation: the `TEST-UNIT` id that owns the rule, the location
 * (`alias`, `file` or `file:line`) and the reason (`TEST-UNIT-014`, `TEST-UNIT-013`,
 * `TEST-UNIT-051`; DEC-061).
 *
 * Every task collects its violations and fails once with all of them, so a red run
 * reports the whole set instead of the first one.
 */
data class Violation(val testId: String, val location: String, val reason: String) : Serializable {
    override fun toString(): String = "$testId: $location: $reason"
}

/**
 * An accumulated set of violations, rendered one per line for the failure message.
 */
internal class ViolationLog {

    private val violations = mutableListOf<Violation>()

    fun add(testId: String, location: String, reason: String) {
        violations += Violation(testId, location, reason)
    }

    fun add(violation: Violation) {
        violations += violation
    }

    fun isEmpty(): Boolean = violations.isEmpty()

    /** The failure message: one line per violation, in collection order. */
    fun render(): String = buildString {
        appendLine("The dependency policy failed with ${violations.size} violation(s):")
        violations.forEach { appendLine(it) }
    }
}

package io.github.davidru85.multiverse.buildlogic.hygiene

import java.io.Serializable

/**
 * One collected finding of `verifyRepositoryHygiene` (`TEST-UNIT-026`, `REQ-SEC-002`,
 * `AC-REQ-SEC-002-1`; DEC-062).
 *
 * A finding is deliberately content-free: the rule id that owns it, a reproducible
 * location and a reason. [location] is the root-relative, `/`-separated path, with a line
 * number when one exists and with the blob identity when the finding comes from history.
 * The matched value is never captured, stored, rendered or logged.
 */
internal data class HygieneViolation(
    val ruleId: String,
    val location: String,
    val reason: String,
) : Serializable {

    override fun toString(): String = "$ruleId: $location: $reason"
}

/**
 * The accumulated findings of one run, rendered together and in a deterministic order so
 * that a red build reports the whole set instead of the first match (TASK-016 §6.4).
 */
internal class HygieneViolationLog {

    private val violations = mutableListOf<HygieneViolation>()

    fun add(ruleId: String, location: String, reason: String) {
        violations += HygieneViolation(ruleId, location, reason)
    }

    fun isEmpty(): Boolean = violations.isEmpty()

    fun render(): String = buildString {
        appendLine("The repository hygiene check failed with ${violations.size} violation(s):")
        violations
            .sortedWith(compareBy({ it.ruleId }, { it.location }, { it.reason }))
            .forEach { appendLine(it) }
    }
}

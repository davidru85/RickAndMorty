package io.github.davidru85.multiverse.buildlogic.hygiene

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * `multiverse.repository.hygiene` — the repository's hygiene policy as one build check
 * (TASK-016, `REQ-SEC-002`, `AC-REQ-SEC-002-1`, `TEST-UNIT-026`; DEC-062).
 *
 * Applied by the **root** build script only. It is separate from `multiverse.dependency.policy`
 * on purpose: that plugin owns DEC-061 (catalog pins, rationale and inventory), this one owns
 * the tracked-path and credential rules, and neither can weaken the other.
 *
 * It registers exactly one public task, `verifyRepositoryHygiene`, in the `verification` group,
 * and wires it into the root `check` task so `./gradlew check` and `./gradlew build` enforce it.
 * It adds no repository, no configuration and no dependency, and it reads Git at execution time
 * rather than at configuration time, so it stays configuration-cache compatible.
 */
class RepositoryHygienePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        check(target == target.rootProject) {
            "`multiverse.repository.hygiene` is applied to the root project only; found it on `${target.path}`."
        }

        // `base` is what guarantees the root `check` task exists; applying it twice is a no-op,
        // so the two root policy plugins coexist without ordering.
        target.pluginManager.apply("base")

        val verify = target.tasks.register<RepositoryHygieneTask>("verifyRepositoryHygiene") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-026: scans the commit-eligible working set and every blob reachable from " +
                "all local refs for credentials, and fails when a build output, IDE state, machine-local file " +
                "or credential carrier is tracked or no longer ignored (REQ-SEC-002, AC-REQ-SEC-002-1; DEC-062)."
            repositoryRoot.set(target.layout.projectDirectory)
        }

        target.tasks.named("check").configure { dependsOn(verify) }
    }

    private companion object {
        const val VERIFICATION_GROUP = "verification"
    }
}

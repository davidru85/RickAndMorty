package io.github.davidru85.multiverse.buildlogic.boundaries

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * `multiverse.module.boundaries` — the accepted module graph as one build check
 * (`TASK-017`, `REQ-NFR-001`, `REQ-NFR-009`; `TEST-UNIT-017`, `TEST-UNIT-012`, `TEST-UNIT-043`).
 *
 * Applied by the **root** build script only. It is a third, separate policy plugin: the
 * dependency policy owns `DEC-061` and the repository hygiene owns `DEC-062`, and none of the
 * three can weaken another. It registers exactly one task, `verifyModuleBoundaries`, in the
 * `verification` group, wires it into the root `check`, adds no repository, no configuration and
 * no dependency, and reads the graph at configuration time so the task stays
 * configuration-cache compatible.
 */
class ModuleBoundariesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        check(target == target.rootProject) {
            "`multiverse.module.boundaries` is applied to the root project only; found it on `${target.path}`."
        }

        // `base` guarantees the root `check` task exists; applying it again is a no-op.
        target.pluginManager.apply("base")

        // The graph must be read after every module build script has been evaluated, because a
        // module declares its dependencies in its own script (DEC-057). The property is set from
        // `projectsEvaluated`, still during configuration, so the task itself never touches
        // `Project` and the snapshot remains a configuration-cache input.
        val graph = target.objects.property(ModuleGraphSnapshot::class.java)
        target.gradle.projectsEvaluated {
            graph.set(ModuleBoundaryCapture.capture(target.allprojects, includeStructure = true))
        }

        val featureSources = target.fileTree(target.rootDir) {
            include("feature/*/src/**/*.kt")
            exclude("**/*Test/**", "**/*test/**", "**/build/**")
        }

        val verify = target.tasks.register<VerifyModuleBoundariesTask>("verifyModuleBoundaries") {
            group = VERIFICATION_GROUP
            description = "TEST-UNIT-017/012/043: the module graph of ADR-0001 as amended — project edges, " +
                "source-set kinds, the :core:domain external allow-list and the staged destination/package rules " +
                "(REQ-NFR-001, REQ-NFR-009; DEC-066, DEC-068)."
            this.graph.set(graph)
            this.featureSources.from(featureSources)
            rootDirectory.set(target.layout.projectDirectory)
        }

        target.tasks.named("check").configure { dependsOn(verify) }
    }

    private companion object {
        const val VERIFICATION_GROUP = "verification"
    }
}

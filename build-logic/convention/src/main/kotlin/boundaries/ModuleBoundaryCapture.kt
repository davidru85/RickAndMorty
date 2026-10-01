package io.github.davidru85.multiverse.buildlogic.boundaries

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Reads the module graph at **configuration** time into a serializable snapshot, so
 * `verifyModuleBoundaries` needs no `Project` at execution time and stays configuration-cache
 * compatible (the pattern `DEC-061` established for the dependency policy).
 *
 * Two deliberate choices decide what an "edge" is:
 *
 * - it is a dependency **declared by the module's own build script**, read from
 *   `configuration.dependencies` — never `allDependencies`, which also reports what a
 *   configuration inherits, and therefore never a generated aggregate such as
 *   `metadataCompilationImplementation` or `*CompileClasspath`. `DEC-057` puts every project edge
 *   in the consuming module's script precisely so a violation is attributable to the diff that
 *   introduced it;
 * - it is declared on a **declarable configuration**: a source-set configuration of this module
 *   (`commonMainImplementation`, `androidMainImplementation`, `commonTestImplementation`, …) or
 *   one of the Android module's own dependency blocks. Build tooling that declares project
 *   dependencies for its own purposes — the Kotlin ABI/build-tools classpaths, the compiler-plugin
 *   classpaths, Android Lint's tool configuration, the SwiftPM lock-file dependency carrier — is
 *   not a module boundary and is not evaluated as one.
 */
internal object ModuleBoundaryCapture {

    /** Dependency-block suffixes a source-set or variant configuration may carry. */
    private val SUFFIXES = listOf("implementation", "api", "compileOnly", "runtimeOnly")

    /** The Android modules' own dependency blocks, including the two build variants. */
    private val ANDROID_BLOCKS = buildSet {
        addAll(listOf("implementation", "api", "compileOnly", "runtimeOnly"))
        listOf("test", "androidTest").forEach { block ->
            addAll(listOf("${block}Implementation", "${block}CompileOnly", "${block}RuntimeOnly", "${block}Api"))
        }
        listOf("debug", "release").forEach { variant ->
            addAll(listOf("${variant}Implementation", "${variant}CompileOnly", "${variant}RuntimeOnly", "${variant}Api"))
        }
    }

    fun capture(projects: Iterable<Project>, includeStructure: Boolean): ModuleGraphSnapshot =
        ModuleGraphSnapshot(
            projects
                .map { project -> snapshot(project, includeStructure) }
                .sortedBy { it.path },
        )

    private fun snapshot(project: Project, includeStructure: Boolean): ProjectSnapshot {
        val declarable = declarableConfigurations(project)
        val edges = mutableListOf<DeclaredEdge>()
        val externals = mutableListOf<DeclaredExternalDependency>()

        project.configurations.forEach { configuration ->
            if (configuration.name !in declarable) return@forEach
            val (sourceSet, kind) = classify(configuration.name, project.path)
            configuration.dependencies.forEach { dependency ->
                when (dependency) {
                    is ProjectDependency -> if (dependency.path != project.path) {
                        edges += DeclaredEdge(
                            consumer = project.path,
                            configuration = configuration.name,
                            sourceSet = sourceSet,
                            kind = kind,
                            producer = dependency.path,
                        )
                    }

                    is ExternalModuleDependency -> externals += DeclaredExternalDependency(
                        consumer = project.path,
                        configuration = configuration.name,
                        sourceSet = sourceSet,
                        group = dependency.group.orEmpty(),
                        name = dependency.name,
                    )

                    else -> Unit
                }
            }
        }

        return ProjectSnapshot(
            path = project.path,
            directory = project.projectDir.relativeTo(project.rootDir).invariantSeparatorsPath,
            moduleKind = moduleKindOf(project.path),
            edges = edges.sortedWith(compareBy({ it.configuration }, { it.producer })),
            externalDependencies = externals.sortedWith(compareBy({ it.configuration }, { it.coordinates })),
            sourceFiles = if (includeStructure && project.path.startsWith(":feature:")) {
                productionSources(project)
            } else {
                emptyList()
            },
        )
    }

    /**
     * The configurations this module's build script may declare a dependency on: every source-set
     * configuration of its Kotlin extension plus the Android dependency blocks. A configuration
     * outside this set belongs to build tooling, not to the module graph.
     */
    private fun declarableConfigurations(project: Project): Set<String> = buildSet {
        addAll(ANDROID_BLOCKS)
        val kotlin = project.extensions.findByName("kotlin") as? KotlinMultiplatformExtension ?: return@buildSet
        kotlin.sourceSets.names.forEach { sourceSet ->
            SUFFIXES.forEach { suffix ->
                add(sourceSet + suffix.replaceFirstChar { it.uppercase() })
            }
        }
    }

    /** Source set and kind of a configuration already known to be declarable. */
    private fun classify(configuration: String, projectPath: String): Pair<String, SourceSetKind> {
        val lowered = configuration.lowercase()
        val base = when {
            configuration == lowerSuffix("implementation") -> "implementation"
            else -> configuration
        }
        val stem = SUFFIXES.firstOrNull { lowered.endsWith(it.lowercase()) && configuration.length > it.length }
            ?.let { configuration.dropLast(it.length) }
            ?: configuration

        val kind = when {
            stem == "test" || stem.endsWith("Test") || stem.startsWith("androidTest") -> SourceSetKind.TEST
            stem.startsWith("android") -> SourceSetKind.ANDROID_UI
            projectPath == ":androidApp" && stem.lowercase() == stem && !stem.contains("Test") -> SourceSetKind.ANDROID_UI
            else -> SourceSetKind.PRODUCTION
        }
        return (if (stem.isEmpty()) base else stem) to kind
    }

    private fun lowerSuffix(suffix: String) = suffix

    /**
     * Root-relative paths of the project's production Kotlin/Swift sources: everything under
     * `src/` except a test source-set directory. Test files are irrelevant to the staged rules.
     */
    private fun productionSources(project: Project): List<String> {
        val root = project.rootDir
        return project.fileTree(project.projectDir.resolve("src")) {
            include("**/*.kt", "**/*.swift")
            exclude("**/*Test/**", "**/*test/**", "**/build/**")
        }.files
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
    }
}

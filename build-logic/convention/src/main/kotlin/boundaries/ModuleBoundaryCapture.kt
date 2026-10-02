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

    fun capture(projects: Iterable<Project>): ModuleGraphSnapshot =
        ModuleGraphSnapshot(
            projects
                .map { project -> snapshot(project) }
                .sortedBy { it.path },
        )

    private fun snapshot(project: Project): ProjectSnapshot {
        val declarable = declarableConfigurations(project)
        val edges = mutableListOf<DeclaredEdge>()
        val externals = mutableListOf<DeclaredExternalDependency>()

        project.configurations.forEach { configuration ->
            if (configuration.name !in declarable) return@forEach
            val (sourceSet, kind) = classify(configuration.name, project.path)

            // The **effective** declarations of this architecture-relevant configuration: its own
            // plus every ancestor reachable through `extendsFrom`. A dependency hidden in a custom
            // configuration and inherited into `commonMain` is still an edge of `commonMain`,
            // which is the case a post-merge review reproduced (`GAP-012`).
            effectiveOrigins(configuration, project).forEach { (origin, dependencies) ->
                dependencies.forEach { dependency ->
                    when (dependency) {
                        is ProjectDependency -> if (dependency.path != project.path) {
                            edges += DeclaredEdge(
                                consumer = project.path,
                                configuration = configuration.name,
                                sourceSet = sourceSet,
                                kind = kind,
                                producer = dependency.path,
                                originConfiguration = origin,
                            )
                        }

                        is ExternalModuleDependency -> externals += DeclaredExternalDependency(
                            consumer = project.path,
                            configuration = configuration.name,
                            sourceSet = sourceSet,
                            kind = kind,
                            group = dependency.group.orEmpty(),
                            name = dependency.name,
                            originConfiguration = origin,
                        )

                        else -> Unit
                    }
                }
            }
        }

        return ProjectSnapshot(
            path = project.path,
            directory = project.projectDir.relativeTo(project.rootDir).invariantSeparatorsPath,
            moduleKind = moduleKindOf(project.path),
            edges = edges
                .distinctBy { listOf(it.configuration, it.producer, it.originConfiguration) }
                .sortedWith(compareBy({ it.configuration }, { it.producer }, { it.originConfiguration })),
            externalDependencies = externals
                .distinctBy { listOf(it.configuration, it.coordinates, it.originConfiguration) }
                .sortedWith(compareBy({ it.configuration }, { it.coordinates }, { it.originConfiguration })),
            targets = kotlinTargetsOf(project),
            moduleLocalProperties = moduleLocalPropertiesOf(project),
        )
    }

    /**
     * The KMP targets the project declares, read from the Kotlin extension through reflection.
     *
     * `build-logic` compiles against the Gradle API, not against the Kotlin plugin, so the
     * extension is reached reflectively; a project without the extension (an Android-only or plain
     * module) reports an empty set, which is as much a fact as any other (`TASK-101`, `GAP-018`).
     */
    private fun kotlinTargetsOf(project: Project): List<String> {
        val extension = project.extensions.findByName("kotlin") ?: return emptyList()
        val targets = runCatching { extension.javaClass.getMethod("getTargets").invoke(extension) }.getOrNull() ?: return emptyList()
        val names = (targets as? Iterable<*>)?.mapNotNull { target ->
            runCatching { target?.javaClass?.getMethod("getName")?.invoke(target) as? String }.getOrNull()
        } ?: emptyList()
        return names.sorted()
    }

    /**
     * The opt-in properties this module declares **in its own** `gradle.properties`.
     *
     * `DEC-080` requires the declaration to be module-local. Reading the file, rather than the
     * merged project properties, is what tells a module-local opt-in from an inherited or
     * root-level one.
     */
    private fun moduleLocalPropertiesOf(project: Project): List<String> {
        val file = project.file("gradle.properties")
        if (!file.isFile) return emptyList()
        return file
            .readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) null else trimmed.substringBefore('=').trim()
            }.filter { it.isNotEmpty() }
            .sorted()
    }

    /**
     * The declarations effective on [configuration]: its own dependencies plus those of every
     * configuration reachable through `extendsFrom`, each paired with the declaring
     * configuration. The walk is recursive and cycle-safe, so an inherited edge is attributed to
     * the declaration that introduced it while the diagnostic still names the architecture
     * configuration it reaches.
     */
    private fun effectiveOrigins(
        configuration: org.gradle.api.artifacts.Configuration,
        project: Project,
    ): List<Pair<String, List<org.gradle.api.artifacts.Dependency>>> {
        val byOrigin = linkedMapOf<String, List<org.gradle.api.artifacts.Dependency>>()
        byOrigin[configuration.name] = configuration.dependencies.toList()

        val seen = mutableSetOf(configuration.name)
        val queue = ArrayDeque(configuration.extendsFrom.map { it.name })
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (!seen.add(name)) continue
            val ancestor = project.configurations.findByName(name) ?: continue
            val dependencies = ancestor.dependencies.toList()
            if (dependencies.isNotEmpty()) byOrigin[name] = dependencies
            ancestor.extendsFrom.forEach { queue.add(it.name) }
        }
        return byOrigin.map { (origin, dependencies) -> origin to dependencies }
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

    /**
     * Source set and kind of a configuration already known to be declarable. This is the one
     * classifier in the check: it used to live beside a second, unused implementation, which a
     * review removed so the rule and its classifier cannot drift (`TASK-088`).
     */
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

}

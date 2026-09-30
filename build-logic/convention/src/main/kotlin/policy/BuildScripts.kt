package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * The build-script half of the policy: catalog accessor references and the project
 * path each declaration belongs to (`TEST-UNIT-051`, I5; DEC-061).
 *
 * A reference is a maximal `libs.<segment>...` chain (never `libs.versions.toml`,
 * which is not a Kotlin reference). The chain references the **longest catalog
 * accessor that is a segment prefix of it**, so `libs.androidx.compose.ui.tooling`
 * references the tooling accessor and not `libs.androidx.compose.ui`.
 */
internal object BuildScripts {

    private val CHAIN = Regex("\\blibs(?:\\.[A-Za-z][A-Za-z0-9]*)+")

    /** Accessor -> the project paths whose build script references it, sorted. */
    fun references(scripts: List<File>, rootDir: File, accessors: Set<String>): Map<String, List<String>> {
        val references = mutableMapOf<String, MutableSet<String>>()
        scripts.forEach { script ->
            val projectPath = projectPath(script, rootDir)
            CHAIN.findAll(script.readText()).forEach { match ->
                val chain = match.value
                if (chain == "libs.versions.toml") return@forEach
                longestAccessor(chain, accessors)?.let { accessor ->
                    references.getOrPut(accessor) { sortedSetOf() }.add(projectPath)
                }
            }
        }
        return references.mapValues { (_, paths) -> paths.sorted() }
    }

    /** The longest catalog accessor that is `chain` itself or a segment prefix of it. */
    fun longestAccessor(chain: String, accessors: Set<String>): String? =
        accessors.filter { chain == it || chain.startsWith("$it.") }.maxByOrNull { it.length }

    /**
     * The project path of the build script at [script]:
     * the root script is `:`, and `<dir>/build.gradle.kts` is `:` plus `<dir>` with
     * `/` replaced by `:` (`feature/discovery/build.gradle.kts` -> `:feature:discovery`).
     */
    fun projectPath(script: File, rootDir: File): String {
        val relative = script.relativeTo(rootDir).invariantSeparatorsPath
        val dir = relative.removeSuffix("/build.gradle.kts")
        if (dir == "build.gradle.kts" || dir.isEmpty()) return ":"
        return ":$dir".replace('/', ':')
    }

    /**
     * External coordinates declared inline with an explicit version — three colon-separated
     * segments inside double quotes, with a non-empty group — or a plugin version declared
     * by the Gradle DSL. A project path such as `":core:domain"` has an empty first segment
     * and does not match.
     *
     * The patterns are used by [PinsTask] on `.gradle.kts` files **and** on the policy sources
     * themselves, which is why this file builds no literal coordinate anywhere: an example
     * is composed from a template (a coordinate written as `group:name:version` is assembled
     * from its three placeholder parts, never typed as one string).
     */
    fun inlineVersionedCoordinates(text: String): List<String> {
        val found = mutableListOf<String>()
        COORDINATE.findAll(text).forEach { found += it.value }
        PLUGIN_VERSION.forEach { pattern -> pattern.findAll(text).forEach { found += it.value } }
        return found
    }

    private val COORDINATE = Regex("\"([A-Za-z][A-Za-z0-9_.\\-]*):([A-Za-z0-9_.\\-]+):([^\"/\\s]+)\"")
    private val PLUGIN_VERSION = listOf(
        // Groovy-DSL form: the `version` keyword followed by a quoted numeric version.
        Regex("\\bversion\\s+\"[0-9][^\"]*\""),
        // Kotlin-DSL form: `version(` followed by a quoted numeric version, never a
        // qualified catalog lookup such as a catalog object's `version("alias")` call.
        Regex("(?<![.\\w])version\\s*\\(\\s*\"[0-9][^\"]*\"\\s*\\)"),
    )
}

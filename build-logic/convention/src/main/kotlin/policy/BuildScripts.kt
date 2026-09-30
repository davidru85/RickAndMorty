package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * The build-script half of the policy: catalog accessor references and the project
 * path each declaration belongs to (`TEST-UNIT-051` I5; DEC-061).
 *
 * A reference is a maximal `libs.<segment>...` chain (never `libs.versions.toml`,
 * which is not a Kotlin reference). The chain references the **longest catalog
 * accessor that is a segment prefix of it**, so `libs.androidx.compose.ui.tooling`
 * references the tooling accessor and not `libs.androidx.compose.ui`.
 *
 * The chain is searched in the source with its comments and string literals masked
 * ([KotlinSourceMask], `maskStrings = true`), so a comment or a string that mentions an
 * accessor is not a declaration.
 */
internal object BuildScripts {

    private val CHAIN = Regex("\\blibs(?:\\.[A-Za-z][A-Za-z0-9]*)+")

    /** Accessor -> the project paths whose build script references it, sorted. */
    fun references(scripts: List<File>, rootDir: File, accessors: Set<String>): Map<String, List<String>> {
        val references = mutableMapOf<String, MutableSet<String>>()
        scripts.forEach { script ->
            val projectPath = projectPath(script, rootDir)
            val code = KotlinSourceMask.mask(script.readText(), maskStrings = true)
            CHAIN.findAll(code).forEach { match ->
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

    /** One pattern that finds an external version declared outside the catalog. */
    data class InlineVersionPattern(val pattern: Regex, val description: String)

    /**
     * The forms an external version takes outside the catalog. Every version alternative
     * requires a **digit** right after the opening quote, which is what keeps a catalog
     * lookup such as `catalog.version("android-minSdk")` out of the result; a project
     * path such as `":core:domain"` never matches either, because the coordinate pattern
     * requires a non-empty first segment.
     */
    val INLINE_VERSION_PATTERNS: List<InlineVersionPattern> = listOf(
        // 1. A quoted coordinate with its version: "group:name:version".
        InlineVersionPattern(
            Regex("\"([A-Za-z][A-Za-z0-9_.\\-]*):([A-Za-z0-9_.\\-]+):([^\"/\\s]+)\""),
            "a coordinate with an inline version",
        ),
        // 2. A coordinate completed by concatenation: "group:name:" + version.
        InlineVersionPattern(
            Regex("\"[A-Za-z][A-Za-z0-9_.\\-]*:[A-Za-z0-9_.\\-]+:\"\\s*\\+"),
            "a coordinate with a concatenated version",
        ),
        // 3. The infix or Groovy form: the `version` keyword, then a quoted version.
        InlineVersionPattern(
            Regex("\\bversion\\s+\"[0-9][^\"]*\""),
            "an inline plugin version",
        ),
        // 4. The call form, including a chained `.version("1.2.3")`.
        InlineVersionPattern(
            Regex("\\bversion\\s*\\(\\s*\"[0-9][^\"]*\"\\s*\\)"),
            "an inline plugin version",
        ),
        // 5. A named argument: version = "1.2.3".
        InlineVersionPattern(
            Regex("\\bversion\\s*=\\s*\"[0-9][^\"]*\""),
            "an inline version named argument",
        ),
        // 6. The helper form: kotlin("module", "1.2.3").
        InlineVersionPattern(
            Regex("\\bkotlin\\s*\\(\\s*\"[^\"]+\"\\s*,\\s*\"[0-9][^\"]*\""),
            "an inline helper version",
        ),
        // 7. The dependency-constraint DSL.
        InlineVersionPattern(
            Regex("\\b(?:strictly|require|prefer|useVersion)\\s*\\(\\s*\"[0-9][^\"]*\""),
            "an inline dependency constraint",
        ),
    )

    /**
     * Every external version declared inline in [text]. One match yields one entry.
     */
    fun inlineVersionedCoordinates(text: String): List<String> =
        INLINE_VERSION_PATTERNS.flatMap { it.pattern.findAll(text).map { match -> match.value } }
}

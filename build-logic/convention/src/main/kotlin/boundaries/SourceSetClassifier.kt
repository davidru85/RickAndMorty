package io.github.davidru85.multiverse.buildlogic.boundaries

/**
 * Classifies a Gradle configuration name into the source set that declared the edge and the kind
 * the boundary rules need.
 *
 * The check captures `configuration.dependencies` — what the consuming module's build script
 * declares — so the names it sees are the source-set configurations a module writes
 * (`commonMainImplementation`, `androidMainImplementation`, `commonTestImplementation`, …) plus
 * the plain `implementation`/`api` of an Android module. A generated aggregate configuration
 * (`metadataCompilationImplementation`, `*CompileClasspath`, `*RuntimeClasspath`) inherits its
 * dependencies rather than declaring them and therefore never appears in the snapshot; if one
 * does, it is classified by the same rule and reported under its own name rather than ignored.
 */
internal object SourceSetClassifier {

    private val SUFFIXES = listOf(
        "Implementation",
        "CompileOnly",
        "RuntimeOnly",
        "Api",
        "CompileClasspath",
        "RuntimeClasspath",
    )

    fun classify(configuration: String): Pair<String, SourceSetKind> {
        val base = SUFFIXES.firstOrNull { configuration.endsWith(it) && configuration.length > it.length }
            ?.let { configuration.dropLast(it.length) }
            ?: configuration
        val kind = when {
            base.contains("Test", ignoreCase = false) -> SourceSetKind.TEST
            base.startsWith("android") -> SourceSetKind.ANDROID_UI
            else -> SourceSetKind.PRODUCTION
        }
        return base.ifEmpty { configuration } to kind
    }
}

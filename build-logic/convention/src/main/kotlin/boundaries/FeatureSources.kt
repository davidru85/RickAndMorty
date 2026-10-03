package io.github.davidru85.multiverse.buildlogic.boundaries

import java.io.File

/**
 * The production sources of one feature, read at task execution and inspected by **content**, so a
 * rule can tell a real Kotlin declaration from a directory name, a comment or a string literal
 * (`GAP-012`, `TASK-088`).
 *
 * Paths stay repository-relative. Three facts are extracted, each from comment- and
 * string-masked source: the Kotlin `package` directive, whether the file declares a typed
 * navigation destination, and whether executable code names the app-wide `NavHost`. A destination
 * is the form `DESIGN.md` §4.2 fixes: `@Serializable` plus a `data object`/`data class` or a
 * `@Serializable` `sealed` hierarchy / `enum class` for parameterised routes.
 */
internal data class FeatureSource(
    val path: String,
    val packageName: String?,
    val declaresDestination: Boolean,
    val namesAppWideNavHost: Boolean,
    /**
     * True when the file declares only a **stateless UI placeholder** (`DEC-104`): a composable that
     * renders copy it is given and holds no state, no use case and no data access. Such a feature has
     * no `domain` and no `presentation` layer to place, so `S3` exempts it rather than demanding empty
     * packages.
     */
    val isStatelessUi: Boolean,
)

/** The masked Kotlin facts one feature source contributes to the structure rules. */
internal object FeatureSources {

    private val PACKAGE = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)")
    private val DESTINATION = Regex(
        "(?m)^\\s*@Serializable[^\\n]*\\n?\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*" +
            "((?:public\\s+|internal\\s+)?(?:data\\s+)?(?:object|class|enum\\s+class|sealed\\s+(?:class|interface))\\s+\\w+)",
    )
    private val NAV_HOST = Regex("\\bNavHost\\s*[(<]|\\bNavHost\\b")

    /**
     * A stateless UI placeholder: a `@Composable` function whose file declares no state, no use case
     * and no repository. The masking step has already removed comments and strings, so this reads only
     * executable declarations.
     */
    private val STATELESS_UI_COMPOSABLE = Regex("(?m)^\\s*(?:public\\s+|internal\\s+|private\\s+)?@Composable\\b")
    private val STATE_OR_DATA = Regex(
        "\\b(?:remember\\s*\\{|mutableStateOf|collectAsState|StateFlow|suspend\\s+fun|" +
            "class\\s+\\w*(?:ViewModel|State|UseCase)|interface\\s+\\w*Repository|" +
            "LaunchedEffect|withContext|coroutineScope)\\b",
    )

    /** Every production Kotlin source under [projectDirectory]`/src`, repository-relative. */
    fun of(rootDirectory: File, projectDirectory: File): List<FeatureSource> {
        val src = projectDirectory.resolve("src")
        if (!src.isDirectory) return emptyList()
        return src.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .filterNot { file ->
                file.path.contains("/build/") || file.path.contains("/test/") || file.path.contains("/Test/")
            }
            .map { file -> read(rootDirectory, file) }
            .sortedBy { source -> source.path }
            .toList()
    }

    /** Reads one source file into its masked facts. */
    fun read(rootDirectory: File, file: File): FeatureSource {
        val masked = KotlinSources.mask(file.readText())
        return analyse(file.relativeTo(rootDirectory).invariantSeparatorsPath, masked)
    }

    /**
     * The same facts for source held in memory, so a test can pin one declaration form without a
     * file. The caller passes the **masked** text; [analyseText] masks it first.
     */
    fun analyse(path: String, masked: String): FeatureSource = FeatureSource(
        path = path,
        packageName = PACKAGE.find(masked)?.groupValues?.get(1),
        declaresDestination = DESTINATION.containsMatchIn(masked),
        namesAppWideNavHost = NAV_HOST.containsMatchIn(masked),
        isStatelessUi =
            STATELESS_UI_COMPOSABLE.containsMatchIn(masked) && !STATE_OR_DATA.containsMatchIn(masked),
    )

    /** Masks [text] and analyses it as if it were a file at [path]. */
    fun analyseText(path: String, text: String): FeatureSource = analyse(path, KotlinSources.mask(text))
}

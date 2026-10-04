package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * `TEST-UNIT-027` — the persisted-field inventory (`REQ-SEC-003`, `AC-REQ-SEC-003-1`).
 *
 * `SECURITY.md` §3 *is* the classification table: every persisted field is listed there, and no
 * store may persist a key it does not classify. The rule reads both sides from their real source —
 * the DataStore keys and `NSUserDefaults` keys from `:core:data`, the store file and cache directory
 * names from the shell, and the classification from the documents — and fails when a key exists in
 * code that the document does not classify, or the other way round.
 *
 * The preference keys are the exception that proves the rule: §3 row 4a delegates them to
 * `CONTRACTS.md` `IC-021`, so the contract's two field names are the authority and each platform's
 * store must hold exactly those fields in its own spelling. The response and image caches are
 * separate stores by `REQ-FUNC-021`, so the check reads their real directory values and requires
 * them to differ.
 */
internal object PersistedFieldInventory {
    const val TEST_ID = "TEST-UNIT-027"

    const val SECURITY_DOCUMENT = "docs/SECURITY.md"
    const val CONTRACTS_DOCUMENT = "docs/CONTRACTS.md"

    /** The four store sources and the two shell sources, keyed the way the task receives them. */
    const val FAVORITES_ANDROID = "favorites-android"
    const val FAVORITES_APPLE = "favorites-apple"
    const val SETTINGS_ANDROID = "settings-android"
    const val SETTINGS_APPLE = "settings-apple"
    const val SHELL = "shell"
    const val IMAGE_LOADER = "image-loader"

    /** The DataStore preference-key factories; the argument is the persisted key. */
    private val DATASTORE_KEY = Regex("""PreferencesKey\("([^"]+)"\)""")

    /** A `const val NAME: String = "value"` declaration, as the shells and the Apple stores write it. */
    private val STRING_CONSTANT =
        Regex("""(?m)^\s*(?:public |private |internal )?const val ([A-Za-z0-9_]+)(?::\s*String)?\s*=\s*"([^"]+)"""")

    /** A literal directory argument to `cacheDir`, as the image loader writes it. */
    private val CACHE_LITERAL = Regex("""cacheDir,\s*"([^"]+)"""")

    /** A constant-reference directory argument to `cacheDir`, as the shell writes it. */
    private val CACHE_REFERENCE = Regex("""cacheDir,\s*([A-Za-z][A-Za-z0-9_]*)""")

    /** The §3 classification row header. */
    private val CLASSIFICATION_HEADER = Regex("""^\|\s*#\s*\|\s*Data\s*\|""")

    /** An identifier-shaped code span: lowercase start, then lowercase, digits, `_` or `.`. */
    private val KEY_SPAN = Regex("""^[a-z][a-z0-9_.]*$""")

    private const val IC021_HEADING = "### IC-021"
    private val IC021_FIELD = Regex("""val\s+([A-Za-z][A-Za-z0-9_]*)\s*:""")

    private const val RESPONSE_CACHE_ROW = "Response cache"
    private const val IMAGE_CACHE_ROW = "Image cache"

    /**
     * @param security the `SECURITY.md` whose §3 is the classification authority.
     * @param contracts the `CONTRACTS.md` whose `IC-021` owns the preference field names.
     * @param sources the six real code sources, keyed by [FAVORITES_ANDROID] … [IMAGE_LOADER]; a
     *   `null` value means the source the build declared does not exist, which fails closed.
     */
    fun scan(
        security: File,
        contracts: File,
        sources: Map<String, File?>,
        root: File,
    ): List<Violation> = buildList {
        val missing = sources.filterValues { it == null || !it.isFile }
        missing.forEach { (id, file) ->
            add(
                Violation(
                    TEST_ID,
                    file?.location(root) ?: id,
                    "the declared store source for `$id` is missing, so the inventory cannot be verified; the check fails closed",
                ),
            )
        }
        if (missing.isNotEmpty()) return@buildList
        val existing = sources.mapValues { (_, file) -> file!! }

        val securityLines = security.readLines()
        val docKeys = classificationKeys(securityLines, security, root, this)

        val fields = ic021Fields(contracts)
        if (fields.isEmpty()) {
            add(
                Violation(
                    TEST_ID,
                    contracts.location(root),
                    "`IC-021` declares no `AppSettings` field, so the preference keys have no authority to verify against",
                ),
            )
            return@buildList
        }
        val expectedSettings = fields.map { it.snakeCase() }.toSet()

        val favoriteKeys =
            keysOf(existing.getValue(FAVORITES_ANDROID), DATASTORE_KEY, root, this, "a Preferences key") +
                keysOf(existing.getValue(FAVORITES_APPLE), STRING_CONSTANT, root, this, "a `const val … String` key")
        val androidSettings = keysOf(existing.getValue(SETTINGS_ANDROID), DATASTORE_KEY, root, this, "a Preferences key").keys
        val appleSettings = keysOf(existing.getValue(SETTINGS_APPLE), STRING_CONSTANT, root, this, "a `const val … String` key").keys

        // The settings stores hold exactly the IC-021 fields, in each platform's spelling: the
        // Android key is the field's snake case and the Apple key is its namespaced form. The Apple
        // match is by suffix against the contract's field names, so the check never hard-codes the
        // namespace the document happens to use.
        if (androidSettings != expectedSettings) {
            add(
                Violation(
                    TEST_ID,
                    existing.getValue(SETTINGS_ANDROID).location(root),
                    "the Android settings store declares ${androidSettings.sorted()}; `IC-021` fixes exactly " +
                        "${expectedSettings.sorted()} (`CONTRACTS.md` `IC-021`)",
                ),
            )
        }
        val appleBases = appleSettings.mapNotNull { key -> expectedSettings.firstOrNull { key.endsWith(".$it") } }.toSet()
        if (appleSettings.size != expectedSettings.size || appleBases != expectedSettings) {
            add(
                Violation(
                    TEST_ID,
                    existing.getValue(SETTINGS_APPLE).location(root),
                    "the Apple settings store declares ${appleSettings.sorted()}; `IC-021` fixes exactly the " +
                        "fields ${expectedSettings.sorted()}, each persisted under its own namespaced key",
                ),
            )
        }

        // The favourite keys are classified by name in §3, in both directions.
        (favoriteKeys.keys - docKeys).sorted().forEach { key ->
            add(
                Violation(
                    TEST_ID,
                    favoriteKeys.getValue(key).location(root),
                    "the favourite store key `$key` exists in code but `SECURITY.md` §3 does not classify it",
                ),
            )
        }
        (docKeys - favoriteKeys.keys).sorted().forEach { key ->
            add(
                Violation(
                    TEST_ID,
                    "$SECURITY_DOCUMENT §3",
                    "`SECURITY.md` §3 classifies `$key`, but no store key matches it; the document and the code disagree",
                ),
            )
        }

        // The two caches are separate stores: the document classifies both and the code names two values.
        val securityText = securityLines.joinToString("\n")
        if (RESPONSE_CACHE_ROW !in securityText || IMAGE_CACHE_ROW !in securityText) {
            add(
                Violation(
                    TEST_ID,
                    "$SECURITY_DOCUMENT §6.1",
                    "the storage list names no `$RESPONSE_CACHE_ROW` or no `$IMAGE_CACHE_ROW`, so a persisted cache is unclassified",
                ),
            )
        }
        val shell = existing.getValue(SHELL)
        val imageLoader = existing.getValue(IMAGE_LOADER)
        val responseDirectory = cacheDirectory(shell, root, this)
        val imageDirectory = cacheDirectory(imageLoader, root, this)
        if (responseDirectory == null) {
            add(Violation(TEST_ID, shell.location(root), "no response-cache directory is declared"))
        }
        if (imageDirectory == null) {
            add(Violation(TEST_ID, imageLoader.location(root), "no image-cache directory is declared"))
        }
        if (responseDirectory != null && responseDirectory == imageDirectory) {
            add(
                Violation(
                    TEST_ID,
                    imageLoader.location(root),
                    "the response cache and the image cache share the directory `$responseDirectory`; `REQ-FUNC-021` keeps them separate",
                ),
            )
        }
    }

    /**
     * The keys §3 classifies by name: a code span in a persisted row that looks like an identifier
     * (`favorite_ids`, `multiverse.favorites.ids`). Prose is deliberately not treated as a key.
     */
    private fun classificationKeys(
        lines: List<String>,
        security: File,
        root: File,
        sink: MutableList<Violation>,
    ): Set<String> {
        val header = lines.indexOfFirst { CLASSIFICATION_HEADER.containsMatchIn(it) }
        if (header < 0) {
            sink += Violation(TEST_ID, security.location(root), "`SECURITY.md` §3 classification table was not found")
            return emptySet()
        }
        val keys = mutableSetOf<String>()
        var index = header + 2 // Skip the header and the separator row.
        while (index < lines.size && lines[index].trimStart().startsWith("|")) {
            val cells = MarkdownTable.cells(lines[index])
            if (cells.size >= 4 && cells[3].startsWith("Yes")) {
                MarkdownTable.codeSpans(lines[index])
                    .filter { KEY_SPAN.matches(it) && ('_' in it || '.' in it) }
                    .forEach { keys += it }
            }
            index++
        }
        return keys
    }

    /** The two `AppSettings` field names `IC-021` declares, from its declaration block. */
    private fun ic021Fields(contracts: File): List<String> {
        val text = contracts.readText()
        val heading = text.indexOf(IC021_HEADING)
        if (heading < 0) return emptyList()
        val declaration = text.indexOf("data class AppSettings", heading)
        if (declaration < 0) return emptyList()
        val body = text.substring(declaration).substringBefore(')')
        return IC021_FIELD.findAll(body).map { it.groupValues[1] }.toSet().toList()
    }

    /** Key to the file that declares it; an empty result is itself a finding. */
    private fun keysOf(
        file: File,
        pattern: Regex,
        root: File,
        sink: MutableList<Violation>,
        description: String,
    ): Map<String, File> {
        val keys =
            pattern
                .findAll(file.readText())
                .mapNotNull { match -> match.groupValues.last().takeIf { it.isNotBlank() } }
                .associateWith { file }
        if (keys.isEmpty()) {
            sink += Violation(TEST_ID, file.location(root), "the store declares no $description, so nothing could be verified")
        }
        return keys
    }

    /**
     * The directory a cache uses: a literal `cacheDir` argument, else a declared constant it names.
     * `null` means the file names no cache directory at all, which is a finding rather than a pass.
     */
    private fun cacheDirectory(file: File, root: File, sink: MutableList<Violation>): String? {
        val text = file.readText()
        CACHE_LITERAL.find(text)?.let { return it.groupValues[1] }
        val reference = CACHE_REFERENCE.find(text)?.groupValues?.get(1) ?: return null
        val constants = STRING_CONSTANT.findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toMap()
        if (reference !in constants) {
            sink += Violation(TEST_ID, file.location(root), "the cache directory `$reference` cannot be resolved to a value")
            return null
        }
        return constants.getValue(reference)
    }

    private fun String.snakeCase(): String = replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
}

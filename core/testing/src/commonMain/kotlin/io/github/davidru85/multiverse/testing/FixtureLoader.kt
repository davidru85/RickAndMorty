package io.github.davidru85.multiverse.testing

/**
 * The single seam through which every test reads a committed fixture (`TESTING.md` §4.3,
 * `TASK-024`).
 *
 * Fixtures are committed as ordinary files under `core/testing/src/commonMain/resources/fixtures/`
 * with dated `.meta.json` sidecars; the build embeds their bytes into [GeneratedFixtures] so every
 * target reads the identical content without a filesystem or a test-resource API.
 *
 * A fixture is never inlined in a test: an inlined body drifts from the captured API shape
 * silently, and the sidecar that records *when* and *from where* the bytes came would be lost
 * (`TESTING.md` §4.4).
 */
public object FixtureLoader {

    /** Every fixture name, sorted, excluding the sidecars. */
    public fun names(): List<String> = GeneratedFixtures.names.sorted()

    /**
     * The text of [name].
     *
     * @throws IllegalArgumentException when no fixture with that name exists. Failing loudly is
     *   deliberate: a test that reads a renamed or deleted fixture must break rather than assert
     *   against an empty body.
     */
    public fun text(name: String): String = GeneratedFixtures.contents[name]
        ?: throw IllegalArgumentException(
            "No fixture named `$name`. Available: ${names().joinToString(", ")}",
        )

    /** The bytes of [name], encoded as UTF-8. */
    public fun bytes(name: String): ByteArray = text(name).encodeToByteArray()

    /** The fixture names this seam exposes, for a test that iterates the catalogue. */
    public fun catalogue(): List<String> = names()
}

/**
 * The dated sidecar of one fixture: what it was captured from, when, and what shape it pins
 * (`TESTING.md` §4.3, §4.4).
 *
 * Published totals and headers live here as dated observations rather than as constants in
 * application or test code (`RISK-006`).
 */
public data class FixtureMeta(
    public val fixture: String,
    public val capturedAt: String,
    public val method: String,
    public val path: String,
    public val query: String?,
    public val status: Int,
    public val pins: String,
    public val sha256: String,
    public val bytes: Int,
)

/** The sidecars of the committed fixtures, read through [FixtureLoader]. */
public object FixtureCatalog {

    /** The sidecar of [name], parsed from `<name>.meta.json`. */
    public fun meta(name: String): FixtureMeta {
        val raw = FixtureLoader.text(name + META_SUFFIX)
        return FixtureMeta(
            fixture = field(raw, "fixture") ?: name,
            capturedAt = field(raw, "capturedAt")
                ?: error("fixture `$name` has no capturedAt in its sidecar"),
            method = field(raw, "method") ?: error("fixture `$name` has no method in its sidecar"),
            path = field(raw, "path") ?: error("fixture `$name` has no path in its sidecar"),
            query = field(raw, "query"),
            status = (field(raw, "status") ?: error("fixture `$name` has no status"))
                .toInt(),
            pins = field(raw, "pins") ?: "",
            sha256 = field(raw, "sha256") ?: "",
            bytes = (field(raw, "bytes") ?: "0").toInt(),
        )
    }

    /** Every fixture's sidecar, sorted by fixture name. */
    public fun all(): List<FixtureMeta> = FixtureLoader.names().map(::meta)

    /**
     * A flat string or number field of a sidecar, or `null` when it is absent or `null`.
     *
     * Hand-rolled on purpose: the sidecar is a flat, machine-generated subset of JSON, and
     * depending on a serialization runtime here would make every consumer of the harness
     * configure one before its first test.
     */
    private fun field(raw: String, key: String): String? {
        val match = Regex("\"$key\"\\s*:\\s*(\"([^\"\\\\]*)\"|-?[0-9]+|null)").find(raw) ?: return null
        val value = match.groupValues[1]
        if (value == "null") return null
        return if (value.startsWith("\"")) match.groupValues[2] else value
    }

    private const val META_SUFFIX = ".meta.json"
}

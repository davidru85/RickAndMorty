package io.github.davidru85.multiverse.buildlogic.hygiene

/**
 * The fixed rule registry `verifyRepositoryHygiene` enforces (`TEST-UNIT-026`, `REQ-SEC-002`,
 * `AC-REQ-SEC-002-1`; DEC-062).
 *
 * Two families live here:
 *
 * - the repository-path rules `HYG-01`…`HYG-07`, which decide whether a tracked path or a
 *   whole-repository state is acceptable;
 * - the credential registry `SEC-026-01`…`SEC-026-12`, which decides whether a byte sequence
 *   or a file name looks like a credential.
 *
 * The registry is deliberately fixed and reviewable. The detector source is itself inside the
 * scanned universe, so every pattern is written so that its own text does not match it, and no
 * identifier in this file combines a credential word with an assignable literal. No
 * implementation file is excluded from the scan and no allow-list suppresses a real value.
 *
 * A detector never returns the text it matched: callers receive only a rule id and, where one
 * exists, a line number.
 */
internal object HygieneRules {

    // ---------------------------------------------------------------------------------
    // Repository-path rules (HYG-*)
    // ---------------------------------------------------------------------------------

    /** A path that must be ignored, and the class it belongs to, for HYG-01's report. */
    data class IgnoredPath(val path: String, val category: String)

    /**
     * The §6.1 ignore sentinels. `check-ignore` decides each one; the list is never compared
     * against `.gitignore` text, so a rule with the same effect declared differently still
     * passes (HYG-01).
     */
    val REQUIRED_IGNORED: List<IgnoredPath> = listOf(
        IgnoredPath(".gradle/state", "Gradle build state"),
        IgnoredPath(".kotlin/state", "Kotlin build state"),
        IgnoredPath("module/build/output.bin", "module build output"),
        IgnoredPath("module/.externalNativeBuild/state", "native build state"),
        IgnoredPath("module/.cxx/state", "native build state"),
        IgnoredPath("captures/screen.png", "instrumentation captures"),
        IgnoredPath(".idea/workspace.xml", "IDE state"),
        IgnoredPath("module.iml", "IDE state"),
        IgnoredPath(".vscode/settings.json", "editor state"),
        IgnoredPath(".fleet/settings.json", "editor state"),
        IgnoredPath("DerivedData/App/Build/file", "Apple build output"),
        IgnoredPath("iosApp/.build/debug/file", "Apple build output"),
        IgnoredPath(
            "iosApp/App.xcodeproj/xcuserdata/user.xcuserdatad/xcschemes/xcschememanagement.plist",
            "Xcode per-user state",
        ),
        IgnoredPath(
            "iosApp/App.xcodeproj/project.xcworkspace/xcuserdata/user.xcuserdatad/UserInterfaceState.xcuserstate",
            "Xcode per-user state",
        ),
        IgnoredPath("UserInterfaceState.xcuserstate", "Xcode per-user state"),
        IgnoredPath("local.properties", "machine-local configuration"),
        IgnoredPath(".DS_Store", "machine-local metadata"),
        IgnoredPath("node_modules/package/file", "Node-generated state"),
        IgnoredPath("prompts/task.md", "agent-local input"),
        IgnoredPath(".env", "environment file"),
        IgnoredPath(".env.local", "environment file"),
        IgnoredPath("config/.env.production", "environment file"),
        IgnoredPath("release.jks", "Android signing material"),
        IgnoredPath("release.keystore", "Android signing material"),
        IgnoredPath("certificate.p12", "Apple signing material"),
        IgnoredPath("certificate.pfx", "Apple signing material"),
        IgnoredPath("Profile.mobileprovision", "Apple provisioning material"),
        IgnoredPath("Profile.provisionprofile", "Apple provisioning material"),
        IgnoredPath("id_rsa", "key material"),
        IgnoredPath("id_dsa", "key material"),
        IgnoredPath("id_ecdsa", "key material"),
        IgnoredPath("id_ed25519", "key material"),
        IgnoredPath("private.pem", "key material"),
        IgnoredPath("private.key", "key material"),
        IgnoredPath("google-services.json", "service configuration"),
        IgnoredPath("GoogleService-Info.plist", "service configuration"),
        IgnoredPath("service-account-prod.json", "service configuration"),
        IgnoredPath("credentials-prod.json", "service configuration"),
    )

    /**
     * Files the ignore rules MUST NOT hide: the shared Xcode project data the future iOS app
     * has to track. An over-broad rule that swallows these is a defect (HYG-01's second half).
     */
    val REQUIRED_TRACKABLE: List<String> = listOf(
        "iosApp/App.xcodeproj/project.pbxproj",
        "iosApp/App.xcodeproj/xcshareddata/xcschemes/App.xcscheme",
        "iosApp/App.xcodeproj/project.xcworkspace/contents.xcworkspacedata",
        "iosApp/App.xcworkspace/contents.xcworkspacedata",
        "iosApp/Package.swift",
        "gradle/libs.versions.toml",
        "gradle/wrapper/gradle-wrapper.properties",
        "settings.gradle.kts",
        "build.gradle.kts",
        "docs/DESIGN.md",
    )

    /** Directory segments that are build output wherever they appear. */
    private val BUILD_DIRECTORIES = setOf(
        "build",
        ".gradle",
        ".kotlin",
        ".externalNativeBuild",
        ".cxx",
        "captures",
        "node_modules",
        "DerivedData",
        ".build",
    )

    /** Directory segments that hold editor or per-user state. */
    private val IDE_DIRECTORIES = setOf(".idea", ".vscode", ".fleet", "xcuserdata")

    /** File suffixes that are editor or per-user state. */
    private val IDE_SUFFIXES = listOf(".iml", ".xcuserstate")

    /** Machine-local files that must never be committed. */
    private val MACHINE_LOCAL_FILES = setOf("local.properties", ".DS_Store")

    /** Signing-container suffixes, including the binary ones (SEC-026-09). */
    private val SIGNING_SUFFIXES =
        listOf(".jks", ".keystore", ".p12", ".pfx", ".mobileprovision", ".provisionprofile", ".pem", ".key")

    /** Named key and service-configuration files. */
    private val CARRIER_NAMES = setOf(
        "id_rsa",
        "id_dsa",
        "id_ecdsa",
        "id_ed25519",
        "google-services.json",
        "googleservice-info.plist",
    )

    /** Prefixes of service-configuration JSON files. */
    private val CARRIER_JSON_PREFIXES = listOf("service-account", "credentials")

    /**
     * The path class of a prohibited tracked path (HYG-03), or `null` when the path is
     * ordinary repository content.
     *
     * Classification is segment-aware: a directory named exactly `build` is build output while
     * `build-logic/`, `build.gradle.kts` and `gradle/` are ordinary content.
     */
    fun classifyProhibited(path: String): String? {
        val segments = path.split('/')
        val fileName = segments.last()
        segments.dropLast(1).forEach { segment ->
            if (segment in BUILD_DIRECTORIES) return "build output (`$segment/`)"
            if (segment in IDE_DIRECTORIES) return "IDE or user state (`$segment/`)"
        }
        if (fileName in MACHINE_LOCAL_FILES) return "machine-local file (`$fileName`)"
        if (IDE_SUFFIXES.any { fileName.endsWith(it) }) return "IDE or user state (`$fileName`)"
        return null
    }

    /**
     * The class of a **tracked** path that must not be committed (HYG-03): the build, IDE,
     * user-state and machine-local classes of [classifyProhibited], plus signing material and
     * every credential-carrier path. This is wider than the historical-path classifier on
     * purpose: a tracked `.env` or key container is both a `HYG-03` violation and an owning
     * `SEC-026-*` finding, and the rule names both.
     */
    fun classifyTrackedProhibited(path: String): String? {
        classifyProhibited(path)?.let { return it }
        val fileName = path.substringAfterLast('/')
        if (isCarrier(path)) return "credential or signing material (`$fileName`)"
        if (isEnvironmentFile(path)) return "an environment file (`$fileName`)"
        return null
    }

    /**
     * Whether a path name is a carrier whose presence is itself a finding (SEC-026-09),
     * independent of whether its content is text.
     */
    fun isCarrier(path: String): Boolean {
        val fileName = path.substringAfterLast('/').lowercase()
        if (SIGNING_SUFFIXES.any { fileName.endsWith(it) }) return true
        if (fileName in CARRIER_NAMES) return true
        return fileName.endsWith(".json") && CARRIER_JSON_PREFIXES.any { fileName.startsWith(it) }
    }

    /** Whether a path is a dotenv file, which SEC-026-12 owns as both a name and a content rule. */
    fun isEnvironmentFile(path: String): Boolean {
        val fileName = path.substringAfterLast('/').lowercase()
        return fileName == ".env" || fileName.startsWith(".env.")
    }

    // ---------------------------------------------------------------------------------
    // Credential registry (SEC-026-*)
    // ---------------------------------------------------------------------------------

    const val RULE_PEM_HEADER = "SEC-026-01"
    const val RULE_AWS_ACCESS_ID = "SEC-026-02"
    const val RULE_AWS_SIGNING_VALUE = "SEC-026-03"
    const val RULE_GITHUB = "SEC-026-04"
    const val RULE_SLACK = "SEC-026-05"
    const val RULE_GOOGLE = "SEC-026-06"
    const val RULE_STRIPE = "SEC-026-07"
    const val RULE_JWT = "SEC-026-08"
    const val RULE_CARRIER_FILENAME = "SEC-026-09"
    const val RULE_GENERIC_ASSIGNMENT = "SEC-026-10"
    const val RULE_BASIC_AUTH_URL = "SEC-026-11"
    const val RULE_DOTENV_ASSIGNMENT = "SEC-026-12"

    /** Every rule class the registry implements, in a stable order for reporting. */
    val RULE_IDS: List<String> = listOf(
        RULE_PEM_HEADER,
        RULE_AWS_ACCESS_ID,
        RULE_AWS_SIGNING_VALUE,
        RULE_GITHUB,
        RULE_SLACK,
        RULE_GOOGLE,
        RULE_STRIPE,
        RULE_JWT,
        RULE_CARRIER_FILENAME,
        RULE_GENERIC_ASSIGNMENT,
        RULE_BASIC_AUTH_URL,
        RULE_DOTENV_ASSIGNMENT,
    )

    /**
     * PEM/OpenSSH/PGP private-key block headers (SEC-026-01). Only the header line is
     * matched, never the key body.
     */
    private val PEM_HEADER = Regex("""-----BEGIN [A-Z0-9 ]*PRIVATE KEY[A-Z0-9 ]*-----""")

    /**
     * Fixed-format service tokens, one alternation so a blob is walked once:
     *
     * - cloud access-key identifiers, a two-letter-era prefix plus 16 uppercase alphanumerics
     *   (SEC-026-02);
     * - forge classic and fine-grained tokens (SEC-026-04);
     * - chat token families, including their five prefixes, and chat webhook URLs (SEC-026-05);
     * - search-provider API keys, a four-character prefix plus 35 key characters (SEC-026-06);
     * - payment-provider live and test keys (SEC-026-07);
     * - three-segment JWTs whose header carries the Base64URL JSON prefix (SEC-026-08).
     */
    private val FIXED_FORMAT_PATTERNS = Regex(
        """(?<cloud>\b(?:AKIA|ASIA)[0-9A-Z]{16}\b)""" +
            """|(?<forge>\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}\b)""" +
            """|(?<forgepat>\bgithub_pat_[A-Za-z0-9_]{30,}\b)""" +
            """|(?<chat>\bxox[abprs]-[A-Za-z0-9-]{10,}\b)""" +
            """|(?<chatweb>https://hooks\.slack\.com/services/[A-Za-z0-9/]{20,})""" +
            """|(?<search>\bAIza[0-9A-Za-z_\-]{35}\b)""" +
            """|(?<pay>\b(?:sk|rk)_(?:live|test)_[A-Za-z0-9]{20,}\b)""" +
            """|(?<jwt>\beyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\b)""",
    )

    private val GROUP_RULES = listOf(
        "cloud" to RULE_AWS_ACCESS_ID,
        "forge" to RULE_GITHUB,
        "forgepat" to RULE_GITHUB,
        "chat" to RULE_SLACK,
        "chatweb" to RULE_SLACK,
        "search" to RULE_GOOGLE,
        "pay" to RULE_STRIPE,
        "jwt" to RULE_JWT,
    )

    /** Cloud signing-value assignments, whose value is a 40-character base64-ish string. */
    private val AWS_SIGNING_ASSIGNMENT = Regex(
        """(?i)\baws[_\-\s]?(?:secret|signing)[_\-\s]?access[_\-\s]?key\b\s*[:=]\s*["']?([A-Za-z0-9/+=]{40})""",
    )

    /** The credential-bearing key words, shared by the assignment rules. */
    private const val KEY_WORDS =
        "password|passwd|secret|token|apikey|api[_-]?key|client[_-]?secret|clientsecret|" +
            "access[_-]?key|accesskey|private[_-]?key|privatekey"

    /**
     * The number of `groupValues` entries before the value groups: the whole match plus the
     * three key forms. `groupValues[0]` is the whole match, so `drop(VALUE_GROUP_OFFSET)`
     * leaves the double-quoted, single-quoted and unquoted value groups.
     */
    private const val VALUE_GROUP_OFFSET = 4

    /**
     * An assignment whose key contains one of the documented words and whose value is long
     * enough not to be prose (SEC-026-10).
     *
     * The key may be unquoted (`secret = …`) or quoted in either style (`"client_secret": …`,
     * `'apiKey' => …`), which is how JSON, TOML, YAML and properties files carry credentials.
     * The value may be double-quoted, single-quoted (either may contain spaces) or unquoted;
     * an unquoted value must be the last token of a configuration entry, which is what the
     * trailing terminator lookahead checks, so ordinary prose is not read as an assignment.
     *
     * The value is captured only so a placeholder can be suppressed; it is never reported.
     */
    private val GENERIC_ASSIGNMENT = Regex(
        """(?im)(?:"([A-Za-z0-9_.\-]*(?:$KEY_WORDS)[A-Za-z0-9_.\-]*)"|'([A-Za-z0-9_.\-]*(?:$KEY_WORDS)[A-Za-z0-9_.\-]*)'|([A-Za-z0-9_.\-]*(?:$KEY_WORDS)[A-Za-z0-9_.\-]*))\s*[:=]\s*(?:"([^"]{8,})"|'([^']{8,})'|([^\s"'`;,]{8,}))(?=[ \t]*(?:[,;}\]#]|//|$))""",
    )

    /** An HTTP(S) URL that embeds basic-auth userinfo (SEC-026-11). */
    private val BASIC_AUTH_URL = Regex("""\bhttps?://[^/\s:@]{1,}:[^/\s:@]{1,}@[^\s/]{1,}""")

    /**
     * A dotenv-style assignment on its own line whose variable name contains one of the
     * documented words (SEC-026-12). Anchored to the line start so ordinary prose and code
     * cannot match.
     */
    private val DOTENV_ASSIGNMENT = Regex(
        """(?m)^[ \t]*(?:export[ \t]+)?[A-Za-z0-9_]*(?:TOKEN|SECRET|PASSWORD|PASSWD|API_KEY|""" +
            """CLIENT_SECRET|ACCESS_KEY|PRIVATE_KEY)[A-Za-z0-9_]*[ \t]*=[ \t]*""" +
            """(?:"([^"]*)"|'([^']*)'|([^\s#]+))""",
    )

    /** Values that are unmistakably not credentials, so the assignment rules do not fire on them. */
    private val NON_VALUES = setOf(
        "null",
        "none",
        "redacted",
        "example",
        "dummy",
        "placeholder",
        "changeme",
        "xxx",
        "todo",
    )

    /**
     * Whether a captured assignment value is a non-value. Only unmistakable placeholders are
     * suppressed; a value is never suppressed for containing `test`, `dev` or `local`
     * (TASK-016 §6.4).
     */
    fun isNonValue(rawValue: String?): Boolean {
        val value = (rawValue ?: "").trim().trim('"', '\'')
        if (value.isEmpty()) return true
        if (value.startsWith("<") && value.endsWith(">")) return true
        if (value.startsWith("${'$'}{") && value.endsWith("}")) return true
        if (value.startsWith("{{") && value.endsWith("}}")) return true
        return value.lowercase() in NON_VALUES
    }

    /**
     * Scans text for every content rule and reports findings as (rule id, line number). The
     * matched text never leaves this method.
     */
    fun scanContent(text: CharSequence, onFinding: (ruleId: String, line: Int) -> Unit) {
        val lines = LineIndex(text)

        PEM_HEADER.findAll(text).forEach { onFinding(RULE_PEM_HEADER, lines.lineAt(it.range.first)) }

        FIXED_FORMAT_PATTERNS.findAll(text).forEach { match ->
            val ruleId = GROUP_RULES.firstOrNull { (group, _) -> match.groups[group] != null }?.second ?: return@forEach
            onFinding(ruleId, lines.lineAt(match.range.first))
        }

        AWS_SIGNING_ASSIGNMENT.findAll(text).forEach { match ->
            if (!isNonValue(match.groupValues.getOrNull(1))) {
                onFinding(RULE_AWS_SIGNING_VALUE, lines.lineAt(match.range.first))
            }
        }

        GENERIC_ASSIGNMENT.findAll(text).forEach { match ->
            // Groups 1–3 are the key forms; groups 4–6 are the double-quoted, single-quoted
            // and unquoted value forms. Only the value groups are inspected.
            val value = match.groupValues.drop(VALUE_GROUP_OFFSET).firstOrNull { it.isNotEmpty() }
            if (!isNonValue(value)) {
                onFinding(RULE_GENERIC_ASSIGNMENT, lines.lineAt(match.range.first))
            }
        }

        BASIC_AUTH_URL.findAll(text).forEach { onFinding(RULE_BASIC_AUTH_URL, lines.lineAt(it.range.first)) }

        DOTENV_ASSIGNMENT.findAll(text).forEach { match ->
            val value = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
            if (!isNonValue(value)) onFinding(RULE_DOTENV_ASSIGNMENT, lines.lineAt(match.range.first))
        }
    }

    /** Maps a character offset to a 1-based line number by binary search over the line starts. */
    private class LineIndex(text: CharSequence) {

        private val starts: IntArray

        init {
            var count = 0
            for (index in text.indices) if (text[index] == '\n') count++
            starts = IntArray(count + 1)
            var next = 1
            for (index in text.indices) {
                if (text[index] == '\n') starts[next++] = index + 1
            }
        }

        fun lineAt(offset: Int): Int {
            val target = offset.coerceIn(0, Int.MAX_VALUE)
            var low = 0
            var high = starts.size - 1
            while (low < high) {
                val middle = (low + high + 1) / 2
                if (starts[middle] <= target) low = middle else high = middle - 1
            }
            return low + 1
        }
    }

    /** The credential rule that owns a path, when the path name alone is a finding. */
    fun pathRule(path: String): String? = when {
        isCarrier(path) -> RULE_CARRIER_FILENAME
        isEnvironmentFile(path) -> RULE_DOTENV_ASSIGNMENT
        else -> null
    }
}

package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-027` — the persisted-field inventory (`REQ-SEC-003`, `AC-REQ-SEC-003-1`).
 *
 * The rule reads the agreement between `SECURITY.md` §3 and the real stores in both directions:
 * every declared store key must be classified, every classified key must exist, the settings store
 * must hold exactly the fields `CONTRACTS.md` `IC-021` declares, and the two caches must stay
 * separate directories. The conforming fixture passes; each mutation reproduces one drift the rule
 * exists to catch.
 */
class PersistedFieldInventoryTest {

    private val securityPath = "docs/SECURITY.md"
    private val contractsPath = "docs/CONTRACTS.md"

    private val paths =
        mapOf(
            PersistedFieldInventory.FAVORITES_ANDROID to "core/data/src/androidMain/kotlin/store/FavoritesAndroid.kt",
            PersistedFieldInventory.FAVORITES_APPLE to "core/data/src/iosMain/kotlin/store/FavoritesApple.kt",
            PersistedFieldInventory.SETTINGS_ANDROID to "core/data/src/androidMain/kotlin/store/SettingsAndroid.kt",
            PersistedFieldInventory.SETTINGS_APPLE to "core/data/src/iosMain/kotlin/store/SettingsApple.kt",
            PersistedFieldInventory.SHELL to "androidApp/src/main/java/store/ShellModule.kt",
            PersistedFieldInventory.IMAGE_LOADER to "androidApp/src/main/java/store/ImageLoader.kt",
        )

    private fun doc(vararg lines: String): String = lines.joinToString("\n") + "\n"

    private val security =
        doc(
            "## 3. Data classification and retention",
            "",
            "| # | Data | Example | Stored? | Transmitted? | Personal? | Retention | Notes |",
            "| --- | --- | --- | --- | --- | --- | --- | --- |",
            "| 4 | Favorite ID set | `{\"1\",\"42\"}` | Yes — persisted user state | No | No | Until toggled | " +
                "Android: the string set `favorite_ids`; Apple: `multiverse.favorites.ids` |",
            "| 4a | App preferences | flags | Yes — persisted user state | No | No | Until changed | " +
                "Exactly the fields of `CONTRACTS.md` `IC-021` |",
            "",
            "### 6.1 What is stored, and where",
            "",
            "| Store | Content | Location | Owner module |",
            "| --- | --- | --- | --- |",
            "| Favorite store | the favourite ID set | Android app-private storage | `:core:data` |",
            "| Preferences store | the app preferences | same platform stores | `:core:data` |",
            "| Response cache | public JSON payloads and cache metadata | Android app-private cache directory | `:core:data` |",
            "| Image cache | public image bytes | the image library's memory + disk caches | Android Coil |",
        )

    private val contracts =
        doc(
            "### IC-021 — `AppSettingsRepository`, `AppSettings` and `RemoteProtocol`",
            "",
            "```kotlin",
            "data class AppSettings(",
            "    val soundsEnabled: Boolean = false,",
            "    val remoteProtocol: RemoteProtocol = RemoteProtocol.Rest,",
            ")",
            "```",
        )

    private val favoritesAndroid = doc("package store", "", "val FAVORITE_IDS = stringSetPreferencesKey(\"favorite_ids\")")

    private val favoritesApple = doc("package store", "", "public const val KEY: String = \"multiverse.favorites.ids\"")

    private val settingsAndroid =
        doc(
            "package store",
            "",
            "val SOUNDS_ENABLED = booleanPreferencesKey(\"sounds_enabled\")",
            "val REMOTE_PROTOCOL = stringPreferencesKey(\"remote_protocol\")",
        )

    private val settingsApple =
        doc(
            "package store",
            "",
            "const val SOUNDS_ENABLED = \"multiverse.settings.sounds_enabled\"",
            "const val REMOTE_PROTOCOL = \"multiverse.settings.remote_protocol\"",
        )

    private val shell =
        doc(
            "package store",
            "",
            "public const val FAVORITES_STORE_FILE: String = \"favorites.preferences_pb\"",
            "public const val RESPONSE_CACHE_DIRECTORY: String = \"responses\"",
            "public const val SETTINGS_STORE_FILE: String = \"settings.preferences_pb\"",
            "",
            "cacheStorage = FileCacheStorage(File(appContext.cacheDir, RESPONSE_CACHE_DIRECTORY))",
        )

    private val imageLoader =
        doc(
            "package store",
            "",
            "public fun imageCacheDirectory(context: Context): File = File(context.cacheDir, \"images\")",
        )

    private val defaults: Map<String, String>
        get() =
            mapOf(
                securityPath to security,
                contractsPath to contracts,
                paths.getValue(PersistedFieldInventory.FAVORITES_ANDROID) to favoritesAndroid,
                paths.getValue(PersistedFieldInventory.FAVORITES_APPLE) to favoritesApple,
                paths.getValue(PersistedFieldInventory.SETTINGS_ANDROID) to settingsAndroid,
                paths.getValue(PersistedFieldInventory.SETTINGS_APPLE) to settingsApple,
                paths.getValue(PersistedFieldInventory.SHELL) to shell,
                paths.getValue(PersistedFieldInventory.IMAGE_LOADER) to imageLoader,
            )

    /** Builds the fixture tree; a `null` override leaves the file unwritten. */
    private fun scan(overrides: Map<String, String?> = emptyMap()): List<Violation> {
        val root = kotlin.io.path.createTempDirectory("persisted-fields").toFile()
        val contents = defaults.mapValues { (path, content) -> if (overrides.containsKey(path)) overrides[path] else content }
        contents.forEach { (path, content) ->
            if (content != null) File(root, path).apply { parentFile.mkdirs(); writeText(content) }
        }
        val sources = paths.mapValues { (_, path) -> contents[path]?.let { File(root, path) } }
        return PersistedFieldInventory.scan(File(root, securityPath), File(root, contractsPath), sources, root)
    }

    @Test
    fun `TEST-UNIT-027 the conforming inventory passes`() {
        assertEquals(emptyList(), scan().map { it.toString() })
    }

    @Test
    fun `TEST-UNIT-027 a settings key neither SECURITY md nor IC-021 classifies is reported`() {
        val settings = paths.getValue(PersistedFieldInventory.SETTINGS_ANDROID)
        val findings = scan(mapOf(settings to settingsAndroid + "val SESSION = stringPreferencesKey(\"session_token\")\n"))

        assertTrue(
            findings.any { it.reason.contains("session_token") && it.reason.contains("`IC-021` fixes exactly") },
            findings.map { it.toString() }.toString(),
        )
    }

    @Test
    fun `TEST-UNIT-027 a favourite key SECURITY md does not classify is reported`() {
        val favorites = paths.getValue(PersistedFieldInventory.FAVORITES_ANDROID)
        val findings =
            scan(mapOf(favorites to favoritesAndroid + "val SESSION = stringSetPreferencesKey(\"session_token\")\n"))

        assertTrue(
            findings.any { it.reason.contains("session_token") && it.reason.contains("does not classify") },
            findings.map { it.toString() }.toString(),
        )
    }

    @Test
    fun `TEST-UNIT-027 a key SECURITY md classifies but no store declares is reported`() {
        val findings = scan(mapOf(securityPath to security.replace("`favorite_ids`", "`favorite_ids`, `ghost_key`")))

        assertTrue(findings.any { it.reason.contains("ghost_key") }, findings.map { it.toString() }.toString())
    }

    @Test
    fun `TEST-UNIT-027 the settings store drifting from IC-021 is reported`() {
        val findings =
            scan(
                mapOf(
                    contractsPath to contracts.replace("val remoteProtocol: RemoteProtocol", "val transportProtocol: RemoteProtocol"),
                ),
            )

        assertTrue(
            findings.any { it.reason.contains("`IC-021` fixes exactly") && it.reason.contains("transport_protocol") },
            findings.map { it.toString() }.toString(),
        )
    }

    @Test
    fun `TEST-UNIT-027 a missing store source fails closed`() {
        val findings = scan(mapOf(paths.getValue(PersistedFieldInventory.FAVORITES_APPLE) to null))

        assertTrue(findings.any { it.reason.contains("missing") }, findings.map { it.toString() }.toString())
    }

    @Test
    fun `TEST-UNIT-027 the response and image caches sharing one directory is reported`() {
        val loader = paths.getValue(PersistedFieldInventory.IMAGE_LOADER)
        val findings = scan(mapOf(loader to imageLoader.replace("\"images\"", "\"responses\"")))

        assertTrue(findings.any { it.reason.contains("separate") }, findings.map { it.toString() }.toString())
    }

    // The inventory above only reads the stores it was told about. A store added anywhere else would
    // persist a field §3 never classifies while the check stays green, so every shipped source is
    // also searched for a persistence primitive outside the inventoried stores.

    private val bootstrap =
        doc(
            "package store",
            "",
            "import platform.Foundation.NSUserDefaults",
            "",
            "// Hands the platform store to the inventoried data sources; it writes nothing itself.",
            "val favorites = UserDefaultsFavoritesLocalDataSource(NSUserDefaults.standardUserDefaults)",
        )

    /** Builds a source tree from the inventoried stores plus [extra], and scans every file in it. */
    private fun scanSites(extra: Map<String, String> = emptyMap()): List<Violation> {
        val root = kotlin.io.path.createTempDirectory("persistence-sites").toFile()
        val files = (defaults - securityPath - contractsPath + ("core/ios/src/commonMain/kotlin/store/Bootstrap.kt" to bootstrap) + extra)
        files.forEach { (path, content) -> File(root, path).apply { parentFile.mkdirs(); writeText(content) } }
        val stores = paths.mapValues { (_, path) -> File(root, path) }
        return PersistedFieldInventory.scanPersistenceSites(files.keys.map { File(root, it) }, stores, root)
    }

    @Test
    fun `TEST-UNIT-027 the inventoried stores and a store-free bootstrap pass the persistence-site scan`() {
        assertEquals(emptyList(), scanSites().map { it.toString() })
    }

    @Test
    fun `TEST-UNIT-027 a preferences key declared outside the inventoried stores is reported`() {
        val path = "feature/discovery/src/androidMain/kotlin/store/RecentSearches.kt"
        val findings = scanSites(mapOf(path to doc("package store", "", "val RECENT = stringSetPreferencesKey(\"recent_searches\")")))

        assertTrue(
            findings.any { it.location.startsWith(path) && it.reason.contains("outside the inventoried stores") },
            findings.map { it.toString() }.toString(),
        )
    }

    @Test
    fun `TEST-UNIT-027 a user-defaults write outside the inventoried stores is reported`() {
        val path = "core/data/src/iosMain/kotlin/store/Onboarding.kt"
        val findings =
            scanSites(
                mapOf(
                    path to
                        doc(
                            "package store",
                            "",
                            "import platform.Foundation.NSUserDefaults",
                            "",
                            "fun markSeen(defaults: NSUserDefaults) = defaults.setBool(true, \"multiverse.onboarding.seen\")",
                        ),
                ),
            )

        assertTrue(findings.any { it.location.startsWith(path) }, findings.map { it.toString() }.toString())
    }

    @Test
    fun `TEST-UNIT-027 a shared-preferences store anywhere in shipped code is reported`() {
        val path = "androidApp/src/main/java/store/Legacy.kt"
        val findings =
            scanSites(mapOf(path to doc("package store", "", "fun open(context: Context) = context.getSharedPreferences(\"legacy\", 0)")))

        assertTrue(findings.any { it.location.startsWith(path) }, findings.map { it.toString() }.toString())
    }

    @Test
    fun `TEST-UNIT-027 a Swift app-storage property in the iOS app is reported`() {
        val path = "iosApp/App/SettingsView.swift"
        val findings = scanSites(mapOf(path to doc("import SwiftUI", "", "struct SettingsView { @AppStorage(\"seen\") var seen = false }")))

        assertTrue(findings.any { it.location.startsWith(path) }, findings.map { it.toString() }.toString())
    }

    @Test
    fun `TEST-UNIT-027 test sources and comments are not shipped persistence sites`() {
        val findings =
            scanSites(
                mapOf(
                    "core/data/src/androidHostTest/kotlin/store/StoreTest.kt" to
                        doc("package store", "", "val PROBE = stringPreferencesKey(\"probe\")"),
                    "core/data/src/commonMain/kotlin/store/Notes.kt" to
                        doc("package store", "", "// The Android store is a stringSetPreferencesKey(\"favorite_ids\") in DataStore."),
                    "iosApp/Tests/SettingsTests.swift" to doc("import XCTest", "", "let defaults = UserDefaults(suiteName: \"test\")"),
                ),
            )

        assertEquals(emptyList(), findings.map { it.toString() })
    }

    @Test
    fun `TEST-UNIT-027 an empty shipped source set fails closed`() {
        val root = kotlin.io.path.createTempDirectory("persistence-empty").toFile()
        val findings = PersistedFieldInventory.scanPersistenceSites(emptyList(), emptyMap(), root)

        assertTrue(findings.any { it.reason.contains("fails closed") }, findings.map { it.toString() }.toString())
    }
}

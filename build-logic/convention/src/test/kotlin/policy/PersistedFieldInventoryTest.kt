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
}

package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File

/**
 * `TEST-UNIT-028` — no microphone or speech permission in either shipped app (`REQ-SEC-004`,
 * `AC-REQ-SEC-004-1`).
 *
 * `SECURITY.md` §8.1 forbids a `RECORD_AUDIO` entry and an `NSSpeechRecognitionUsageDescription` /
 * `NSMicrophoneUsageDescription` entry while voice search is deferred. The audit covers the whole
 * repository rather than only the shipped manifest, so a new manifest or plist that introduces one
 * is caught wherever it lands; `iosApp/` does not exist yet, so finding no plist is a pass and not
 * an error. The shipped manifest must be present: the check fails closed when it is missing.
 *
 * A source manifest cannot show a permission that a library manifest merges into the APK, so
 * [scanArtifact] reads the shipped artifact's own permission table as well (`TESTING.md` §17 places
 * `TEST-UNIT-028` in the app shells for that reason).
 */
internal object PermissionAbsencePolicy {
    const val TEST_ID = "TEST-UNIT-028"

    /** The Android microphone permission no shipped app may declare (`SECURITY.md` §8.1). */
    const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"

    /** The only shipped Android manifest, which must exist for a clean run to mean anything. */
    const val SHIPPED_MANIFEST = "androidApp/src/main/AndroidManifest.xml"

    /** The usage-description keys that gate a speech or microphone capability on Apple platforms. */
    private val USAGE_DESCRIPTIONS = listOf("NSSpeechRecognitionUsageDescription", "NSMicrophoneUsageDescription")

    /** A requested permission in `aapt2 dump permissions`, including the `uses-permission-sdk-NN` form. */
    private val USES_PERMISSION = Regex("""^uses-permission(?:-sdk-\d+)?:\s*name='([^']+)'""")

    /**
     * @param manifests every `AndroidManifest.xml` under the repository, minus build output.
     * @param plists every `Info.plist` family file under the repository.
     * @param root the repository root, used only to render repository-relative paths.
     */
    fun scan(manifests: Collection<File>, plists: Collection<File>, root: File): List<Violation> = buildList {
        val shipped = manifests.firstOrNull { it.relativeTo(root).invariantSeparatorsPath == SHIPPED_MANIFEST }
        if (shipped == null || !shipped.isFile) {
            add(
                Violation(
                    TEST_ID,
                    SHIPPED_MANIFEST,
                    "the shipped Android manifest is missing, so the permission audit cannot be performed; the check fails closed",
                ),
            )
        }
        manifests.filter { it.isFile }.sortedBy { it.invariantSeparatorsPath }.forEach { manifest ->
            manifest.readLines().forEachIndexed { index, line ->
                if (line.contains(RECORD_AUDIO)) {
                    add(
                        Violation(
                            TEST_ID,
                            "${manifest.location(root)}:${index + 1}",
                            "`$RECORD_AUDIO` is declared, which `REQ-SEC-004` forbids in every shipped app",
                        ),
                    )
                }
            }
        }
        plists.filter { it.isFile }.sortedBy { it.invariantSeparatorsPath }.forEach { plist ->
            val text = plist.readText()
            USAGE_DESCRIPTIONS.filter { text.contains(it) }.forEach { key ->
                add(
                    Violation(
                        TEST_ID,
                        plist.location(root),
                        "`$key` is declared, which `REQ-SEC-004` forbids while voice search is deferred",
                    ),
                )
            }
        }
    }

    /**
     * The artifact half: the permission table `aapt2 dump permissions` prints for the APK that ships.
     *
     * A table without its `package:` line is not a permission table, so a missing or unreadable APK
     * fails closed instead of passing on an empty list.
     *
     * @param dump the tool's standard output for the shipped APK.
     * @param apk the APK's repository-relative path, used as the finding's location.
     */
    fun scanArtifact(dump: String, apk: String): List<Violation> = buildList {
        val lines = dump.lines().map { it.trim() }
        if (lines.none { it.startsWith("package:") }) {
            add(
                Violation(
                    TEST_ID,
                    apk,
                    "`aapt2 dump permissions` returned no permission table for the shipped APK; the check fails closed",
                ),
            )
            return@buildList
        }
        lines
            .mapNotNull { USES_PERMISSION.find(it)?.groupValues?.get(1) }
            .filter { it == RECORD_AUDIO }
            .forEach { add(Violation(TEST_ID, apk, "`$it` is merged into the shipped APK, which `REQ-SEC-004` forbids")) }
    }
}

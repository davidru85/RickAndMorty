package io.github.davidru85.multiverse.buildlogic.policy

/**
 * `TEST-UNIT-019` — the Android milestone assembles with the iOS app absent (`REQ-PLAT-004`,
 * `AC-REQ-PLAT-004-1`, `DEC-040`, `TASK-050`).
 *
 * The rule reads two sources, so the property belongs to the build rather than to a checkout that
 * happens to lack `iosApp/`:
 *
 * 1. the release APK's entry names carry no iOS or Kotlin/Native payload, because an Android artifact
 *    that shipped one would make the iOS work a runtime dependency of the Android app;
 * 2. the app's declared release edges name no module that exists only for the Apple target. A module
 *    path matches exactly, so a module that merely shares a prefix is not a finding.
 *
 * Each input that is empty fails closed: an artifact with no entries is not an APK, and an empty edge
 * or iOS-only set would make the second half inspect nothing.
 */
internal object MilestoneIndependencePolicy {
    const val TEST_ID = "TEST-UNIT-019"

    /** Path fragments only an iOS or Kotlin/Native payload can produce, compared case-insensitively. */
    private val IOS_PAYLOAD_MARKERS =
        listOf(
            "iosapp/",
            "core/ios",
            "core-ios",
            "kotlin-native",
            "kotlinnative",
            ".framework/",
            "libbackend.a",
            "libclang_rt",
        )

    /**
     * @param apkEntries the release APK's entry names.
     * @param releaseEdges the project paths `:androidApp` declares in its release-reaching configurations.
     * @param iosOnlyModules the project paths whose only purpose is the Apple target.
     */
    fun scan(apkEntries: List<String>, releaseEdges: List<String>, iosOnlyModules: List<String>): List<Violation> =
        buildList {
            val edges = releaseEdges.map { it.trim() }.filter { it.isNotEmpty() }
            val iosOnly = iosOnlyModules.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            if (apkEntries.isEmpty()) add(Violation(TEST_ID, "release APK", "the artifact has no entries; the check fails closed"))
            if (edges.isEmpty()) {
                add(Violation(TEST_ID, ":androidApp", "no release dependency edge was supplied; the check fails closed"))
            }
            if (iosOnly.isEmpty()) {
                add(Violation(TEST_ID, ":androidApp", "no iOS-only module was declared, so the graph half proves nothing; the check fails closed"))
            }
            apkEntries
                .filter { name -> IOS_PAYLOAD_MARKERS.any { name.contains(it, ignoreCase = true) } }
                .forEach { name ->
                    add(
                        Violation(
                            TEST_ID,
                            "release APK",
                            "the release APK carries `$name`, which is iOS/Kotlin-Native payload; the Android " +
                                "deliverable must be independent of the iOS one (AC-REQ-PLAT-004-1)",
                        ),
                    )
                }
            edges.filter { it in iosOnly }.forEach { edge ->
                add(
                    Violation(
                        TEST_ID,
                        ":androidApp",
                        "the release configuration reaches `$edge`, a module that exists only for the Apple target; " +
                            "`REQ-PLAT-004` requires the Android milestone to build with no iOS artifact at all",
                    ),
                )
            }
        }
}

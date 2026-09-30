package io.github.davidru85.multiverse.buildlogic.policy

/**
 * The single reader of the Gradle wrapper's distribution version, shared by P5 and R7
 * so the two checks can never disagree about the same URL (`TEST-UNIT-014`,
 * `TEST-UNIT-013`; DEC-061).
 */
internal object GradleWrapper {

    /**
     * The version named by [distributionUrl], or `null` when the URL is absent or does
     * not name an exact Gradle release distribution.
     */
    fun version(distributionUrl: String?): String? =
        distributionUrl?.let { VERSION.find(it)?.groupValues?.get(1) }

    private val VERSION = Regex("gradle-([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)-(?:bin|all)\\.zip$")
}

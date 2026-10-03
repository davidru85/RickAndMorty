package io.github.davidru85.multiverse.core.presentation.parity

import java.io.File

/** The two platforms whose copy must match (`REQ-UX-008`). */
enum class Platform { ANDROID, APPLE }

/** One way a platform's copy departs from the canonical list or from the other platform. */
sealed interface ParityIssue {
    /** A locale's resource file does not exist on [platform]. */
    data class MissingResource(
        val platform: Platform,
        val locale: String,
    ) : ParityIssue

    /** A canonical [key] is absent from [platform]'s [locale]. */
    data class MissingKey(
        val platform: Platform,
        val locale: String,
        val key: String,
    ) : ParityIssue

    /** [platform]'s [locale] defines a [key] the canonical list does not have. */
    data class ExtraKey(
        val platform: Platform,
        val locale: String,
        val key: String,
    ) : ParityIssue

    /** [platform]'s [locale] defines [key] more than once. */
    data class DuplicateKey(
        val platform: Platform,
        val locale: String,
        val key: String,
    ) : ParityIssue

    /** The two platforms carry different copy for [key] in the same [locale]. */
    data class DivergentValue(
        val locale: String,
        val key: String,
        val android: String,
        val apple: String,
    ) : ParityIssue
}

/**
 * The copy-parity verifier of `TEST-UNIT-036` (`DEC-020`, `REQ-UX-008`): for each locale it reads the
 * Android `strings.xml` and the Apple `Localizable.strings` with their own parsers and reports every
 * canonical key that is missing, every key that is not canonical, every duplicate, and every key whose
 * two values differ once each format's escapes and placeholders are read. English is never compared
 * with Spanish.
 *
 * The real resource folders are passed in by `TASK-013` (Android `res/`) and `TASK-060` (the Apple
 * folder holding the `.lproj` directories), with `CopyKeys.all` as [verify]'s canonical list (`CONF-70`).
 */
object CopyParity {
    /** The shipped locales and where each platform keeps them (`REQ-FUNC-013`, `DEC-006`). */
    private val LOCALES =
        listOf(
            Locale("en", androidFolder = "values", appleFolder = "en.lproj"),
            Locale("es", androidFolder = "values-es", appleFolder = "es.lproj"),
        )

    private class Locale(
        val tag: String,
        val androidFolder: String,
        val appleFolder: String,
    )

    fun verify(
        canonical: Set<String>,
        androidResources: File,
        appleResources: File,
    ): List<ParityIssue> =
        LOCALES.flatMap { locale ->
            val android = File(androidResources, "${locale.androidFolder}/strings.xml").takeIf { it.isFile }?.let(AndroidStrings::parse)
            val apple = File(appleResources, "${locale.appleFolder}/Localizable.strings").takeIf { it.isFile }?.let(AppleStrings::parse)
            buildList {
                listOf(Platform.ANDROID to android, Platform.APPLE to apple).forEach { (platform, strings) ->
                    if (strings == null) {
                        add(ParityIssue.MissingResource(platform, locale.tag))
                        return@forEach
                    }
                    strings.duplicates.sorted().forEach { add(ParityIssue.DuplicateKey(platform, locale.tag, it)) }
                    (canonical - strings.entries.keys).sorted().forEach { add(ParityIssue.MissingKey(platform, locale.tag, it)) }
                    (strings.entries.keys - canonical).sorted().forEach { add(ParityIssue.ExtraKey(platform, locale.tag, it)) }
                }
                if (android != null && apple != null) {
                    (android.entries.keys intersect apple.entries.keys).sorted().forEach { key ->
                        val androidValue = android.entries.getValue(key)
                        val appleValue = apple.entries.getValue(key)
                        if (Placeholders.android(androidValue) != Placeholders.apple(appleValue)) {
                            add(ParityIssue.DivergentValue(locale.tag, key, androidValue, appleValue))
                        }
                    }
                }
            }
        }
}

/** A parsed resource file: the first value of each key, and the keys defined more than once. */
class ParsedStrings(
    val entries: Map<String, String>,
    val duplicates: List<String>,
) {
    companion object {
        fun of(pairs: List<Pair<String, String>>): ParsedStrings {
            val entries = linkedMapOf<String, String>()
            val duplicates = mutableListOf<String>()
            pairs.forEach { (key, value) -> if (entries.putIfAbsent(key, value) != null) duplicates += key }
            return ParsedStrings(entries, duplicates.distinct())
        }
    }
}

/**
 * Each format's placeholders, read into one neutral form: `{position:text}` or `{position:number}`,
 * with an unpositioned placeholder numbered by its order, and `%%` as a literal `%`.
 */
internal object Placeholders {
    private val ANDROID = Regex("""%%|%(?:(\d+)\$)?([sd])""")
    private val APPLE = Regex("""%%|%(?:(\d+)\$)?(@|lld|llu|ld|li|lu|d|i|u)""")

    fun android(value: String): String = neutral(value, ANDROID) { if (it == "s") "text" else "number" }

    fun apple(value: String): String = neutral(value, APPLE) { if (it == "@") "text" else "number" }

    private fun neutral(
        value: String,
        pattern: Regex,
        kind: (String) -> String,
    ): String {
        var next = 0
        return pattern.replace(value) { match ->
            if (match.value == "%%") return@replace "%"
            next++
            val position = match.groupValues[1].ifEmpty { "$next" }
            "{$position:${kind(match.groupValues[2])}}"
        }
    }
}

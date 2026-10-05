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
 * with Spanish. A plural key is compared per quantity form: the Android `<plurals>` items against the
 * Apple `Localizable.stringsdict` forms, each as `key#quantity` (`DEC-132`).
 *
 * The real resource folders are passed in by `TASK-013` (Android `res/`) and `TASK-060` (the Apple
 * folder holding the `.lproj` directories), with `CopyKeys.all` as [verify]'s canonical list (`CONF-70`).
 */
object CopyParity {
    /** The quantity forms every plural key carries on both platforms, in both locales (`DEC-132`). */
    private val QUANTITIES = listOf("one", "other")

    /** The entry name of one quantity form of a plural key, as both parsers read it. */
    fun pluralForm(
        key: String,
        quantity: String,
    ): String = "$key#$quantity"

    /** The canonical entries: every plain key, and every quantity form of every plural key. */
    fun canonical(
        plain: Set<String>,
        plurals: Set<String>,
    ): Set<String> = plain + plurals.flatMap { key -> QUANTITIES.map { pluralForm(key, it) } }

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

    /**
     * The Android-completeness entry (`DEC-100`, `TASK-013`): the same parser and the same
     * canonical comparison as [verify], applied to the one Android copy set that ships, in both
     * locales. The cross-platform comparison stays [verify]'s, because it needs the Apple folder
     * `TASK-060` adds.
     */
    fun verifyAndroid(
        canonical: Set<String>,
        androidResources: File,
    ): List<ParityIssue> =
        LOCALES.flatMap { locale ->
            val android = File(androidResources, "${locale.androidFolder}/strings.xml").takeIf { it.isFile }?.let(AndroidStrings::parse)
            buildList {
                if (android == null) {
                    add(ParityIssue.MissingResource(Platform.ANDROID, locale.tag))
                    return@buildList
                }
                android.duplicates.sorted().forEach { add(ParityIssue.DuplicateKey(Platform.ANDROID, locale.tag, it)) }
                (canonical - android.entries.keys).sorted().forEach { add(ParityIssue.MissingKey(Platform.ANDROID, locale.tag, it)) }
                (android.entries.keys - canonical).sorted().forEach { add(ParityIssue.ExtraKey(Platform.ANDROID, locale.tag, it)) }
            }
        }

    fun verify(
        canonical: Set<String>,
        androidResources: File,
        appleResources: File,
    ): List<ParityIssue> =
        LOCALES.flatMap { locale ->
            val android = File(androidResources, "${locale.androidFolder}/strings.xml").takeIf { it.isFile }?.let(AndroidStrings::parse)
            val apple = File(appleResources, locale.appleFolder).takeIf { it.isDirectory }?.let(::appleCopy)
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

/**
 * One Apple locale folder's copy: `Localizable.strings` plus, when present, the plural forms of
 * `Localizable.stringsdict`. Without the strings file the locale is missing; without the stringsdict
 * there are no plural forms, and each canonical form is then reported missing.
 */
private fun appleCopy(folder: File): ParsedStrings? {
    val strings = File(folder, "Localizable.strings").takeIf { it.isFile }?.let(AppleStrings::parse) ?: return null
    val plurals = File(folder, "Localizable.stringsdict").takeIf { it.isFile }?.let(AppleStringsDict::parse)
    return if (plurals == null) strings else strings + plurals
}

/** A parsed resource file: the first value of each key, and the keys defined more than once. */
class ParsedStrings(
    val entries: Map<String, String>,
    val duplicates: List<String>,
) {
    /** The entries of both files read as one set; a key defined in both is a duplicate. */
    operator fun plus(other: ParsedStrings): ParsedStrings =
        of(entries.toList() + other.entries.toList()).let { merged ->
            ParsedStrings(merged.entries, (duplicates + other.duplicates + merged.duplicates).distinct())
        }

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

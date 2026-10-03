package io.github.davidru85.multiverse.core.designsystem.copy

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `TEST-UNIT-008` — every canonical copy key resolves in `en` and `es` and follows the device
 * locale (`REQ-FUNC-013`, `AC-REQ-FUNC-013-2`, `DEC-100`): switching the device language changes
 * the resolved copy with no code change.
 *
 * The values are read from the module's own resources, so a key that resolves to the same string
 * in both locales (an untranslated string) fails, and a key that is missing from one file throws
 * the platform's resource-not-found error.
 */
@RunWith(RobolectricTestRunner::class)
class CopyLocaleResolutionTest {

    @Test
    @Config(qualifiers = "en")
    fun `TEST-UNIT-008 given_the_english_locale_when_a_key_resolves_then_it_returns_the_english_string`() {
        assertEquals("Multiverse Explorer", resolve("app_name"))
        assertEquals("Characters", resolve("nav_characters"))
        assertEquals("Alive", resolve("status_alive"))
    }

    @Test
    @Config(qualifiers = "es")
    fun `TEST-UNIT-008 given_the_spanish_locale_when_the_same_key_resolves_then_it_returns_the_spanish_string`() {
        assertEquals("Multiverse Explorer", resolve("app_name"))
        assertEquals("Personajes", resolve("nav_characters"))
        assertEquals("Vivo", resolve("status_alive"))
    }

    @Test
    @Config(qualifiers = "es")
    fun `TEST-UNIT-008 given_the_spanish_locale_when_every_key_resolves_then_no_value_is_empty_or_untranslated`() {
        // A key whose Spanish value is left empty or copied from English is the failure this case
        // exists for. The brand marks are exempt by design — "Multiverse" and "EXPLORER" are the
        // product's identity in every locale (`UI_SPEC.md` §6.1) — and the app name is a proper
        // noun. Every other key must differ from its English value.
        val untranslated =
            CopyResolver.names()
                .filterNot { it in BRAND_MARKS }
                .filter { key -> resolve(key).isBlank() || resolve(key) == resolveInLocale(key, "en") }
        assertEquals("every non-brand key must be translated in Spanish", emptyList<String>(), untranslated.sorted())
    }

    @Test
    @Config(qualifiers = "en")
    fun `TEST-UNIT-008 given_a_key_that_is_not_registered_when_it_resolves_then_the_resolver_reports_it`() {
        assertEquals(null, CopyResolver.resourceId("not_a_registered_key"))
    }

    private fun resolve(key: String): String {
        val id = requireNotNull(CopyResolver.resourceId(key)) { "no resource registered for `$key`" }
        return application().getString(id)
    }

    /** The value of [key] under another locale, for the untranslated comparison. */
    private fun resolveInLocale(
        key: String,
        language: String,
    ): String {
        val id = requireNotNull(CopyResolver.resourceId(key))
        val context = application()
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale(language))
        return context.createConfigurationContext(configuration).getString(id)
    }

    /** Robolectric's application context, so no extra test dependency is needed. */
    private fun application(): Context = RuntimeEnvironment.getApplication()

    private companion object {
        /**
         * Keys whose value is deliberately identical in both locales: the brand marks and the app
         * name are proper nouns the product keeps in every language (`UI_SPEC.md` §6.1).
         */
        val BRAND_MARKS = setOf("app_name", "splash_wordmark", "splash_wordmark_sub")
    }
}

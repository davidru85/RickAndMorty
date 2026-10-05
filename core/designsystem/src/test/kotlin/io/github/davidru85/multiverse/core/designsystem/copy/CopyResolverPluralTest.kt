package io.github.davidru85.multiverse.core.designsystem.copy

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `TEST-UNIT-082`, the Android resolver half — plural copy (`DEC-132`, `REQ-FUNC-013`): a plural key
 * resolves through the resolver's plural table to the `<plurals>` resource, and the platform picks the
 * quantity form for the count in each locale, with the count substituted as a number.
 */
@RunWith(RobolectricTestRunner::class)
class CopyResolverPluralTest {
    @Test
    @Config(qualifiers = "en")
    fun `TEST-UNIT-082 given_the_english_locale_when_the_episode_line_resolves_then_one_and_other_read_their_own_form`() {
        assertEquals("Appears in 1 episode", resolve("detail_appears_in_episodes", 1))
        assertEquals("Appears in 51 episodes", resolve("detail_appears_in_episodes", 51))
    }

    @Test
    @Config(qualifiers = "es")
    fun `TEST-UNIT-082 given_the_spanish_locale_when_the_episode_line_resolves_then_one_and_other_read_their_own_form`() {
        assertEquals("Aparece en 1 episodio", resolve("detail_appears_in_episodes", 1))
        assertEquals("Aparece en 51 episodios", resolve("detail_appears_in_episodes", 51))
    }

    @Test
    @Config(qualifiers = "en")
    fun `TEST-UNIT-082 given_a_plain_key_when_it_is_looked_up_as_a_plural_then_the_resolver_has_no_entry`() {
        assertEquals("a plain key is never a plural", null, CopyResolver.pluralResourceId("action_retry"))
        assertEquals("a plural key is never a plain string", null, CopyResolver.resourceId("detail_appears_in_episodes"))
    }

    private fun resolve(
        key: String,
        count: Int,
    ): String {
        val id = requireNotNull(CopyResolver.pluralResourceId(key)) { "no plural resource registered for `$key`" }
        return application().resources.getQuantityString(id, count, count)
    }

    private fun application(): Context = RuntimeEnvironment.getApplication()
}

package io.github.davidru85.multiverse.core.designsystem.copy

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `TEST-UNIT-096` — an unregistered copy key (`DEC-144`, `TASK-115`).
 *
 * `CopyResolver` failed with `requireNotNull` in every build, so a key the parity tests missed would
 * crash the **release** app, where its KDoc promised a loud failure "in debug". The failure stays for
 * debug and test builds; a release build renders the key itself instead.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en")
class CopyMissingKeyTest {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun restoreDefault() {
        CopyResolver.missingKeyPolicy = CopyResolver.MissingKeyPolicy.FAIL
    }

    @Test
    fun `TEST-UNIT-096 given_a_release_build_when_a_key_is_unregistered_then_the_key_itself_is_shown`() {
        CopyResolver.missingKeyPolicy = CopyResolver.MissingKeyPolicy.SHOW_KEY

        compose.setContent { Text(CopyResolver.copy("no_such_key")) }

        compose.onNodeWithText("no_such_key").assertExists()
    }

    @Test
    fun `TEST-UNIT-096 given_a_debug_or_test_build_when_a_key_is_unregistered_then_resolution_fails`() {
        assertThrows(IllegalStateException::class.java) {
            compose.setContent { Text(CopyResolver.copy("no_such_key")) }
            compose.waitForIdle()
        }
    }
}

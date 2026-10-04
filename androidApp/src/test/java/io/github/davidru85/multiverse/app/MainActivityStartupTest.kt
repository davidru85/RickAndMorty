package io.github.davidru85.multiverse.app

import android.os.Looper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import io.github.davidru85.multiverse.core.data.favorites.FavoritesLocalDataSource
import io.github.davidru85.multiverse.core.data.settings.AppSettingsLocalDataSource
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.testing.FakeAppSettingsStore
import io.github.davidru85.multiverse.testing.FakeCatalogue
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import io.github.davidru85.multiverse.testing.FakeFavoritesStore
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** TEST-UNIT-057: launch the real composition root; requests and images stay behind in-memory seams. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = MultiverseApplication::class)
class MainActivityStartupTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @After
    fun closeGraph() {
        ApplicationProvider.getApplicationContext<MultiverseApplication>().applicationScope.cancel()
        stopKoin()
    }

    @Test
    fun `TEST-UNIT-057 given_the_production_graph_when_main_activity_starts_then_its_image_port_resolves`() {
        loadKoinModules(
            module {
                single<FavoritesLocalDataSource> { FakeFavoritesStore() }
                single<AppSettingsLocalDataSource> { FakeAppSettingsStore() }
                single<CharacterRepository> { FakeCharacterRepository(FakeCatalogue(listOf(FakeCatalogue.character("1")))) }
                single<ImageSeam> {
                    object : ImageSeam {
                        @Composable
                        override fun rememberPainter(
                            url: String,
                            widthPx: Int,
                            heightPx: Int,
                        ): ImageSeamResult = ImageSeamResult.Loading
                    }
                }
            },
        )
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().visible().get()
            val decor = activity.window.decorView
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, 1080, 1920)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            compose.onNodeWithContentDescription("Loading characters").assertIsDisplayed()
        }
    }
}

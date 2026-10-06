package io.github.davidru85.multiverse.app.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.davidru85.multiverse.app.R
import io.github.davidru85.multiverse.app.splash.BrandedSplash
import io.github.davidru85.multiverse.app.splash.SplashExitCrossfadeMillis
import io.github.davidru85.multiverse.app.splash.rememberReduceMotion
import io.github.davidru85.multiverse.core.designsystem.components.MultiverseNavigationBar
import io.github.davidru85.multiverse.core.designsystem.components.NavigationDestination
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.image.CharacterAccentPolicy
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.image.LocalCharacterAccentPolicy
import io.github.davidru85.multiverse.core.designsystem.image.LocalPortalMark
import io.github.davidru85.multiverse.core.designsystem.motion.LocalPortraitTransitionScope
import io.github.davidru85.multiverse.core.designsystem.motion.LocalPortraitVisibilityScope
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.presentation.CopyKeys
import io.github.davidru85.multiverse.core.presentation.DetailHandoff
import io.github.davidru85.multiverse.core.presentation.splash.SplashGate
import io.github.davidru85.multiverse.feature.characterdetail.navigation.CharacterDetail
import io.github.davidru85.multiverse.feature.characterdetail.ui.CharacterDetailRoute
import io.github.davidru85.multiverse.feature.discovery.navigation.CharacterList
import io.github.davidru85.multiverse.feature.discovery.ui.DiscoveryRoute
import io.github.davidru85.multiverse.feature.episodes.navigation.Episodes
import io.github.davidru85.multiverse.feature.episodes.ui.EpisodesPlaceholder
import io.github.davidru85.multiverse.feature.favorites.navigation.Favorites
import io.github.davidru85.multiverse.feature.favorites.ui.FavoritesRoute
import io.github.davidru85.multiverse.feature.settings.navigation.Settings
import io.github.davidru85.multiverse.feature.settings.ui.SettingsRoute

/**
 * The app-wide navigation graph (`TASK-044`, `DESIGN.md` §4.2): one `NavHost` composed from the
 * features' typed route declarations, inside the design system's theme and scaffold.
 *
 * No feature references this `NavHost` (`S2`): each feature declares its own destination and the
 * shell is the only place that knows all five. The four top-level destinations are the navigation
 * bar's, and they restore state and keep a single top (`findStartDestination` + `launchSingleTop`),
 * so tapping a tab never grows the back stack (`REQ-FUNC-008`).
 *
 * Each destination hosts its feature's route: Discovery, the Detail, the Episodes placeholder,
 * Favorites and Settings.
 */
@Composable
public fun MultiverseApp(
    navController: NavHostController = rememberNavController(),
    /** The readiness gate (`TASK-007`); a caller may supply one, and `null` skips the splash. */
    splashGate: SplashGate? = null,
    /** The one image seam every portrait draws through (`DEC-097`); the shell owns the loader. */
    imageSeam: ImageSeam = NO_IMAGE_SEAM,
    /** The card-to-detail hand-off (`IC-025`); the shell owns the one instance. */
    detailHandoff: DetailHandoff? = null,
    /** The card accents (`UI_SPEC.md` §5.4); `null` keeps every card Surface Container High. */
    accentPolicy: CharacterAccentPolicy? = null,
) {
    val handoff = detailHandoff ?: remember { DetailHandoff() }
    val context = LocalContext.current
    MultiverseTheme {
        Surface(color = MultiverseColors.surface, modifier = Modifier.fillMaxSize()) {
            // The splash is an overlay that leaves with a 380 ms crossfade once the gate completes
            // (`UI_SPEC.md` §6.1). With no gate supplied the app starts at the destination, which is
            // how a case or a preview renders a screen directly.
            val splashVisible = if (splashGate != null) !rememberSplashReady(splashGate) else false
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            val destinations = topLevelDestinations()
            val selectedKey =
                destinations
                    .firstOrNull { destination ->
                        when (destination.key) {
                            KEY_CHARACTERS -> currentDestination?.hasRoute(CharacterList::class) == true
                            KEY_EPISODES -> currentDestination?.hasRoute(Episodes::class) == true
                            KEY_FAVORITES -> currentDestination?.hasRoute(Favorites::class) == true
                            KEY_SETTINGS -> currentDestination?.hasRoute(Settings::class) == true
                            else -> false
                        }
                    }?.key ?: KEY_CHARACTERS

            val reduceMotion = rememberReduceMotion()
            val (enterMotion, exitMotion) = PortraitMotion.transition(reduceMotion)
            // The Detail is a pushed screen with no navigation bar (Figma `21:1217`); before the graph
            // resolves its first destination the start destination, Characters, is the one shown.
            val onTopLevel = currentDestination?.hasRoute(CharacterDetail::class) != true
            // Edge to edge: each destination handles its own insets, so the Detail hero can draw under
            // the status bar while the top-level screens start below it.
            Column(modifier = Modifier.fillMaxSize()) {
                // The card→Detail shared element (`REQ-FUNC-009`, `DEC-135`): the layout's scope reaches
                // the design system's portraits through a CompositionLocal, and Reduce Motion withholds
                // it, so the destination change is the cross-fade alone (`AC-REQ-FUNC-009-2`).
                SharedTransitionLayout(modifier = Modifier.weight(1f)) {
                    CompositionLocalProvider(
                        LocalPortraitTransitionScope provides if (reduceMotion) null else this,
                        // The one brand mark every failed portrait shows (`UI_SPEC.md` §5.3, `AC-REQ-FUNC-005-2`).
                        LocalPortalMark provides painterResource(R.drawable.ic_portal_mark),
                        LocalCharacterAccentPolicy provides accentPolicy,
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = CharacterList,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = { enterMotion },
                            exitTransition = { exitMotion },
                            popEnterTransition = { enterMotion },
                            popExitTransition = { exitMotion },
                            // The back gesture seeks the same pop, so the Detail fades in place while its
                            // portrait returns to the card. Left unset, Navigation Compose shrinks the
                            // Detail toward the centre and the two come apart (`GAP-048`).
                            predictivePopEnterTransition = { enterMotion },
                            predictivePopExitTransition = { exitMotion },
                        ) {
                            composable<CharacterList> {
                                TopLevelInsets {
                                    TransitionDestination {
                                        DiscoveryRoute(
                                            seam = imageSeam,
                                            onOpenDetail = { card ->
                                                // The card travels through `IC-025` before the destination changes, so the
                                                // hero can animate from its bounds and the known fields render at once.
                                                handoff.publish(card)
                                                navController.navigate(CharacterDetail(card.id.value))
                                            },
                                        )
                                    }
                                }
                            }
                            composable<CharacterDetail> { entry ->
                                TransitionDestination {
                                    val route = entry.toRoute<CharacterDetail>()
                                    val id = CharacterId(route.id)
                                    CharacterDetailRoute(
                                        id = id,
                                        seam = imageSeam,
                                        header = handoff.consume(id),
                                        onBack = { navController.popBackStack() },
                                        onShare = { card -> context.startActivity(shareCharacterIntent(context, card)) },
                                    )
                                }
                            }
                            // Episodes and Favorites render their specified placeholder screens (`TASK-008`);
                            // both take "Browse characters" as a callback that selects the Characters
                            // destination without pushing a route (`REQ-FUNC-008`, `DEC-099`).
                            composable<Episodes> {
                                TopLevelInsets {
                                    EpisodesPlaceholder(
                                        onBrowseCharacters = { navController.selectTopLevel(KEY_CHARACTERS) },
                                        illustration = painterResource(R.drawable.ic_play_circle),
                                    )
                                }
                            }
                            composable<Favorites> {
                                TopLevelInsets {
                                    TransitionDestination {
                                        FavoritesRoute(
                                            seam = imageSeam,
                                            handoff = handoff,
                                            onOpenDetail = { id -> navController.navigate(CharacterDetail(id.value)) },
                                            onBrowseCharacters = { navController.selectTopLevel(KEY_CHARACTERS) },
                                            illustration = painterResource(R.drawable.ic_heart_outline),
                                        )
                                    }
                                }
                            }
                            // The Settings screen (`TASK-074`/`TASK-076`) resolves its own state holder.
                            composable<Settings> {
                                TopLevelInsets {
                                    SettingsRoute()
                                }
                            }
                        }
                    }
                }
                // The bar grows in and out with the destination change, on the same duration and curve,
                // so it never pushes the leaving Detail up by its whole height in one frame; Reduce
                // Motion shows and hides it at once.
                val (barEnter, barExit) = PortraitMotion.barTransition(reduceMotion)
                AnimatedVisibility(visible = onTopLevel, enter = barEnter, exit = barExit) {
                    MultiverseNavigationBar(
                        destinations = destinations,
                        selectedKey = selectedKey,
                        barDescription = null,
                        onSelect = { key -> navController.selectTopLevel(key) },
                    )
                }
            }
            AnimatedVisibility(
                visible = splashVisible,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(SplashExitCrossfadeMillis)),
            ) {
                BrandedSplash()
            }
        }
    }
}

/**
 * The insets of a top-level destination: it starts below the status bar and clear of a display cutout,
 * and the navigation bar under it takes the bottom inset. The Detail does not use this, because its
 * hero draws under the status bar (Figma `21:1217`).
 */
@Composable
private fun TopLevelInsets(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        content()
    }
}

/**
 * Provides this destination's animated-visibility scope to the design system's portraits, so a portrait
 * drawn here can take part in the card→Detail shared element (`DEC-135`).
 */
@Composable
private fun AnimatedContentScope.TransitionDestination(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPortraitVisibilityScope provides this, content = content)
}

@Composable
private fun rememberSplashReady(gate: SplashGate): Boolean {
    // Saved state, not `remember`: a configuration change recreates the activity, and the splash
    // must not replay over a list that is already on screen (`DEC-136`).
    val ready = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gate) {
        if (ready.value) return@LaunchedEffect
        gate.awaitReady()
        ready.value = true
    }
    return ready.value
}

/**
 * The four top-level destinations in the order of `UI_SPEC.md` §4.1: Characters, Episodes,
 * Favorites, Settings. The icons are the Material Symbols path data the app bundles, declared in
 * `MultiverseIcons`; the bar itself binds their colours once.
 */
@Composable
internal fun topLevelDestinations(): List<NavigationDestination> =
    listOf(
        NavigationDestination(KEY_CHARACTERS, CopyResolver.copy(CopyKeys.NAV_CHARACTERS.value), MultiverseIcons.Groups, MultiverseIcons.GroupsOutlined),
        NavigationDestination(
            KEY_EPISODES,
            CopyResolver.copy(CopyKeys.NAV_EPISODES.value),
            MultiverseIcons.PlayArrow,
            MultiverseIcons.PlayArrowOutlined,
        ),
        NavigationDestination(
            KEY_FAVORITES,
            CopyResolver.copy(CopyKeys.NAV_FAVORITES.value),
            MultiverseIcons.Favorite,
            MultiverseIcons.FavoriteOutlined,
        ),
        NavigationDestination(KEY_SETTINGS, CopyResolver.copy(CopyKeys.NAV_SETTINGS.value), MultiverseIcons.Settings, MultiverseIcons.SettingsOutlined),
    )

/**
 * The seam a destination uses when the caller supplies none: every portrait stays in its placeholder
 * state. It exists so a preview or a case can compose a screen without a loader, and so no screen has
 * to branch on whether the shell wired one (`DEC-097`).
 */
private val NO_IMAGE_SEAM: ImageSeam =
    object : ImageSeam {
        @Composable
        override fun rememberPainter(
            url: String,
            widthPx: Int,
            heightPx: Int,
        ): ImageSeamResult = ImageSeamResult.Loading
    }

/** A stable key per top-level destination, so the bar never depends on a route type. */
internal const val KEY_CHARACTERS: String = "characters"
internal const val KEY_EPISODES: String = "episodes"
internal const val KEY_FAVORITES: String = "favorites"
internal const val KEY_SETTINGS: String = "settings"

/** Selects a top-level destination without pushing a second copy of it (`REQ-FUNC-008`). */
internal fun NavHostController.selectTopLevel(key: String) {
    val route: Any =
        when (key) {
            KEY_CHARACTERS -> CharacterList
            KEY_EPISODES -> Episodes
            KEY_FAVORITES -> Favorites
            else -> Settings
        }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

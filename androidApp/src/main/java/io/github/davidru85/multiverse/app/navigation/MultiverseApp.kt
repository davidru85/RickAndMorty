package io.github.davidru85.multiverse.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.davidru85.multiverse.core.designsystem.components.MultiverseNavigationBar
import io.github.davidru85.multiverse.core.designsystem.components.NavigationDestination
import io.github.davidru85.multiverse.core.designsystem.copy.CopyResolver
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.feature.characterdetail.navigation.CharacterDetail
import io.github.davidru85.multiverse.feature.discovery.navigation.CharacterList
import io.github.davidru85.multiverse.feature.episodes.navigation.Episodes
import io.github.davidru85.multiverse.feature.favorites.navigation.Favorites
import io.github.davidru85.multiverse.feature.settings.navigation.Settings

/**
 * The app-wide navigation graph (`TASK-044`, `DESIGN.md` §4.2): one `NavHost` composed from the
 * features' typed route declarations, inside the design system's theme and scaffold.
 *
 * No feature references this `NavHost` (`S2`): each feature declares its own destination and the
 * shell is the only place that knows all five. The four top-level destinations are the navigation
 * bar's, and they restore state and keep a single top (`findStartDestination` + `launchSingleTop`),
 * so tapping a tab never grows the back stack (`REQ-FUNC-008`).
 *
 * The destinations' **content** arrives with B5 (`TASK-001`, `TASK-002`, `TASK-006`, `TASK-074`) and
 * with phase 4.3's placeholders (`TASK-008`); until then each one renders its section title in the
 * Discovery headline position, which is the staging `DEC-099` records.
 */
@Composable
public fun MultiverseApp(
    navController: androidx.navigation.NavHostController = rememberNavController(),
    /** The readiness gate (`TASK-007`); a caller may supply one, and `null` skips the splash. */
    splashGate: io.github.davidru85.multiverse.app.splash.SplashGate? = null,
) {
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
                destinations.firstOrNull { destination ->
                    when (destination.key) {
                        KEY_CHARACTERS -> currentDestination?.hasRoute(CharacterList::class) == true
                        KEY_EPISODES -> currentDestination?.hasRoute(Episodes::class) == true
                        KEY_FAVORITES -> currentDestination?.hasRoute(Favorites::class) == true
                        KEY_SETTINGS -> currentDestination?.hasRoute(Settings::class) == true
                        else -> false
                    }
                }?.key ?: KEY_CHARACTERS

            Column(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = CharacterList,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<CharacterList> { SectionPlaceholder(titleKey = "nav_characters") }
                    composable<CharacterDetail> { entry ->
                        val route = entry.toRoute<CharacterDetail>()
                        SectionPlaceholder(titleKey = "nav_characters", subtitle = route.id)
                    }
                    // Episodes and Favorites render their specified placeholder screens (`TASK-008`);
                    // both take "Browse characters" as a callback that selects the Characters
                    // destination without pushing a route (`REQ-FUNC-008`, `DEC-099`).
                    composable<Episodes> {
                        io.github.davidru85.multiverse.feature.episodes.ui.EpisodesPlaceholder(
                            onBrowseCharacters = { navController.selectTopLevel(KEY_CHARACTERS) },
                        )
                    }
                    composable<Favorites> {
                        io.github.davidru85.multiverse.feature.favorites.ui.FavoritesEmptyState(
                            onBrowseCharacters = { navController.selectTopLevel(KEY_CHARACTERS) },
                        )
                    }
                    // Characters and Settings render the section title in the Discovery headline
                    // position, with their content staged to `TASK-001` and `TASK-074` (`DEC-099`).
                    composable<Settings> { SectionPlaceholder(titleKey = "nav_settings") }
                }
                MultiverseNavigationBar(
                    destinations = destinations,
                    selectedKey = selectedKey,
                    barDescription = null,
                    onSelect = { key -> navController.selectTopLevel(key) },
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = splashVisible,
                enter = androidx.compose.animation.fadeIn(),
                exit =
                    androidx.compose.animation.fadeOut(
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                io.github.davidru85.multiverse.app.splash.SplashExitCrossfadeMillis,
                            ),
                    ),
            ) {
                io.github.davidru85.multiverse.app.splash.BrandedSplash()
            }
        }
    }
}

/**
 * Whether the gate has completed, held across recompositions: the splash waits once per process, so a
 * configuration change does not restart it. The work runs in the composition's scope, which cancels it
 * with the composition rather than leaking a request.
 */
@Composable
private fun rememberSplashReady(gate: io.github.davidru85.multiverse.app.splash.SplashGate): Boolean {
    val ready = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(gate) {
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
        NavigationDestination(KEY_CHARACTERS, CopyResolver.copy("nav_characters"), MultiverseIcons.Groups, MultiverseIcons.Groups),
        NavigationDestination(KEY_EPISODES, CopyResolver.copy("nav_episodes"), MultiverseIcons.PlayArrow, MultiverseIcons.PlayArrowOutlined),
        NavigationDestination(KEY_FAVORITES, CopyResolver.copy("nav_favorites"), MultiverseIcons.Favorite, MultiverseIcons.FavoriteOutlined),
        NavigationDestination(KEY_SETTINGS, CopyResolver.copy("nav_settings"), MultiverseIcons.Settings, MultiverseIcons.SettingsOutlined),
    )

/** A stable key per top-level destination, so the bar never depends on a route type. */
internal const val KEY_CHARACTERS: String = "characters"
internal const val KEY_EPISODES: String = "episodes"
internal const val KEY_FAVORITES: String = "favorites"
internal const val KEY_SETTINGS: String = "settings"

/** Selects a top-level destination without pushing a second copy of it (`REQ-FUNC-008`). */
internal fun androidx.navigation.NavHostController.selectTopLevel(key: String) {
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

/**
 * The section title a destination renders until its screen exists (`DEC-099`): the Discovery
 * headline position, plus the navigation bar the shell always shows.
 */
@Composable
internal fun SectionPlaceholder(
    titleKey: String,
    subtitle: String? = null,
) {
    val title = CopyResolver.copy(titleKey)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .semantics { contentDescription = title },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MultiverseColors.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MultiverseColors.onSurfaceVariant,
            )
        }
    }
}

import MultiverseExplorer
import SwiftUI

/// The iOS app shell (`UI_SPEC.md` §6/§7, `REQ-FUNC-007`, `REQ-FUNC-008`, `TASK-054`/`TASK-055`).
///
/// It composes the four destinations, the branded splash and the platform chrome, and it carries the
/// integration the features must not own. The navigation rule lives in [ShellNavigation], so the view
/// holds no state of its own and the "Browse characters" contract is testable without rendering.
///
/// Each destination hosts the **real** feature screen where one exists (`TASK-055` delivered
/// Discovery, Detail and Favorites), so the shell is the composition root for the screens exactly as
/// `:androidApp` is on Android: it resolves the shared graph through `MultiverseBootstrap` and hands
/// the screen a state holder, and a screen never resolves a dependency itself.
struct RootView: View {
    @StateObject private var navigation = ShellNavigation()

    /// The splash gate: the branded splash shows until its floor elapses, and it completes with no
    /// network (`AC-REQ-FUNC-007-3`).
    @State private var splashVisible = true

    /// The card the user tapped, so the detail destination can render its header before any request
    /// (`AC-REQ-FUNC-002-1`).
    @State private var opened: CharacterCardUi?

    var body: some View {
        TabView(
            selection: Binding(
                get: { navigation.selected },
                set: { navigation.select($0) }
            )
        ) {
            ForEach(ShellDestination.allCases) { destination in
                DestinationView(
                    destination: destination,
                    navigation: navigation,
                    opened: $opened
                )
                .tabItem {
                    Text(LocalizedCopy.shared.text(for: destination.labelKey))
                }
                .tag(destination)
            }
        }
        .tint(MultiverseBrandColors.portalGlow)
        .overlay {
            if splashVisible {
                BrandedSplashView(reduceMotion: UIAccessibility.isReduceMotionEnabled)
                    .transition(.opacity)
                    .task {
                        // The splash is a floor, not a network wait, so a cold start with no
                        // connectivity still reaches the shell (`TASK-007`).
                        try? await Task.sleep(nanoseconds: UInt64(SplashTiming.minimumSeconds * 1_000_000_000))
                        withAnimation(.easeInOut(duration: SplashTiming.exitCrossfadeSeconds)) {
                            splashVisible = false
                        }
                    }
            }
        }
        // Single appearance (`UI_SPEC.md` §9): the app is dark-only and never follows the system.
        .preferredColorScheme(.dark)
    }
}

/// The splash's timing contract (`UI_SPEC.md` §6.1, `TASK-007`): the same 1.2 s floor and exit
/// crossfade the Android gate uses, so the two shells cannot disagree about how long it lasts.
enum SplashTiming {
    static let minimumSeconds: Double = 1.2
    static let exitCrossfadeSeconds: Double = 0.38
}

/// One destination's surface (`TASK-054`).
///
/// Discovery, Favorites and Settings host their feature screens; Episodes is the designed placeholder
/// of `UI_SPEC.md` §6.4 until `DEF-002` re-admits the real screen. Each destination keeps its own
/// title and copy, so a tab never borrows another's words.
struct DestinationView: View {
    let destination: ShellDestination
    @ObservedObject var navigation: ShellNavigation
    @Binding var opened: CharacterCardUi?

    var body: some View {
        NavigationStack {
            switch destination {
            case .characters:
                // Discovery owns its own state holder: the pager is per-screen (`IC-014`), so the
                // holder builds it from the shared graph through the bootstrap.
                DiscoveryHost { card in
                    opened = card
                }
            case .favorites:
                FavoritesHost(onBrowse: { navigation.browseCharacters() })
            case .episodes:
                // The designed coming-soon screen (`UI_SPEC.md` §6.4, `TASK-056`).
                EpisodesPlaceholderScreen(onBrowseCharacters: { navigation.browseCharacters() })
            case .settings:
                SettingsHost()
            }
        }
        .navigationDestination(item: $opened) { card in
            // The detail destination renders from the card the list already had, so its header is
            // present in the first frame (`AC-REQ-FUNC-002-1`).
            DetailHost(card: card)
        }
    }

}

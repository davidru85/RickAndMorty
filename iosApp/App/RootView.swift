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

    /// The card each destination opened, so the detail destination can render its header before any
    /// request (`AC-REQ-FUNC-002-1`). It is kept per destination: every tab has its own stack, and a
    /// card opened in one must not push a detail onto the others.
    @State private var openedCards: [ShellDestination: CharacterCardUi] = [:]

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
                    opened: Binding(
                        get: { openedCards[destination] },
                        set: { openedCards[destination] = $0 }
                    )
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
                        // The shared gate the Android shell awaits (`IC-026`, `DEC-136`): at least
                        // 1.2 s, at most 3 s, ended by the first page's outcome — so a cold start with
                        // no connectivity still reaches the shell at the ceiling.
                        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                            MultiverseBootstrap.shared.awaitSplashReady { continuation.resume() }
                        }
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

/// The splash's motion timing (`UI_SPEC.md` §6.1, `TASK-007`): the portal's 1.2 s cycle — the shared
/// gate's minimum, so one full acceleration always plays — and the exit crossfade the Android shell
/// uses. How long the splash stays is the shared gate's decision (`IC-026`), not a constant here.
enum SplashTiming {
    static let minimumSeconds: Double = 1.2
    static let exitCrossfadeSeconds: Double = 0.38
}

/// One destination's surface (`TASK-054`).
///
/// Discovery, Favorites and Settings host their feature screens; Episodes is the designed placeholder
/// of `UI_SPEC.md` §6.4 until `DEF-002` re-admits the real screen. Each destination keeps its own
/// title and copy, so a tab never borrows another's words.
struct DestinationView<Detail: View>: View {
    let destination: ShellDestination
    @ObservedObject var navigation: ShellNavigation
    @Binding var opened: CharacterCardUi?

    /// The detail destination for an opened card. The shell passes `DetailHost`; a test passes a probe,
    /// because the real host starts the shared graph and its network client.
    let detail: (CharacterCardUi) -> Detail

    var body: some View {
        NavigationStack {
            root
                // Inside the stack, on its root: SwiftUI drops a `navigationDestination` attached to
                // the stack itself, so a tapped card would push nothing. The detail renders from the
                // card the list already had, so its header is present in the first frame
                // (`AC-REQ-FUNC-002-1`).
                .navigationDestination(item: $opened) { card in
                    detail(card)
                }
        }
    }

    @ViewBuilder
    private var root: some View {
        switch destination {
        case .characters:
            // Discovery owns its own state holder: the pager is per-screen (`IC-014`), so the holder
            // builds it from the shared graph through the bootstrap.
            DiscoveryHost { card in
                opened = card
            }
        case .favorites:
            // A favourite opens the same detail destination Discovery's cards do (`IC-020`).
            FavoritesHost(
                onBrowse: { navigation.browseCharacters() },
                onOpenDetail: { card in opened = card }
            )
        case .episodes:
            // The designed coming-soon screen (`UI_SPEC.md` §6.4, `TASK-056`).
            EpisodesPlaceholderScreen(onBrowseCharacters: { navigation.browseCharacters() })
        case .settings:
            SettingsHost()
        }
    }

}

extension DestinationView where Detail == DetailHost {
    /// The shell's destination, whose detail is the real `DetailHost`.
    init(destination: ShellDestination, navigation: ShellNavigation, opened: Binding<CharacterCardUi?>) {
        self.init(destination: destination, navigation: navigation, opened: opened, detail: { DetailHost(card: $0) })
    }
}

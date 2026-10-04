import MultiverseExplorer
import SwiftUI

/// The iOS app shell (`UI_SPEC.md` §6/§7, `REQ-FUNC-007`, `REQ-FUNC-008`, `TASK-054`).
///
/// It composes the four top-level destinations, the branded splash and the platform chrome, and it
/// carries the integration the features must not own. The navigation rule lives in
/// [ShellNavigation], so the view holds no state of its own and the "Browse characters" contract is
/// testable without rendering.
///
/// The destinations are the shared Kotlin route declarations (`ShellDestination.sharedRouteName`),
/// read from `:core:ios`; no Swift-only route enum exists (`CONTRACTS.md` §7.1 R4). The copy comes
/// from the committed `Localizable.strings` through the canonical key list, so both platforms show
/// the same words (`REQ-UX-008`).
struct RootView: View {
    @StateObject private var navigation = ShellNavigation()

    /// The splash gate: `TASK-054` shows the branded splash until the shared gate resolves, and the
    /// splash completes with no network (`AC-REQ-FUNC-007-3`).
    @State private var splashVisible = true

    var body: some View {
        ZStack {
            TabView(
                selection: Binding(
                    get: { navigation.selected },
                    set: { navigation.select($0) }
                )
            ) {
                ForEach(ShellDestination.allCases) { destination in
                    DestinationView(destination: destination, navigation: navigation)
                        .tabItem {
                            Text(LocalizedCopy.shared.text(for: destination.labelKey))
                        }
                        .tag(destination)
                }
            }
            .tint(MultiverseBrandColors.portalGlow)

            if splashVisible {
                BrandedSplashView(reduceMotion: UIAccessibility.isReduceMotionEnabled)
                    .transition(.opacity)
                    .task {
                        // The splash is a floor, not a network wait: it completes on its own so a
                        // cold start with no connectivity still reaches the shell (`TASK-007`).
                        try? await Task.sleep(nanoseconds: UInt64(SplashTiming.minimumSeconds * 1_000_000_000))
                        withAnimation(.easeInOut(duration: SplashTiming.exitCrossfadeSeconds)) {
                            splashVisible = false
                        }
                    }
            }
        }
        // Single appearance (`UI_SPEC.md` §9): the app is dark-only and never follows the system.
        .preferredColorScheme(.dark)
        .accessibilityIdentifier("shell.root")
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
/// `TASK-055` replaces these with the real screens; until then each is the designed placeholder of
/// `UI_SPEC.md` §6.4 with the canonical copy and the working "Browse characters" action, which is
/// what `AC-REQ-FUNC-008-1` and `-2` require of the shell.
struct DestinationView: View {
    let destination: ShellDestination
    @ObservedObject var navigation: ShellNavigation

    var body: some View {
        NavigationStack {
            switch destination {
            case .characters:
                // The Discovery surface is `TASK-055`'s; until it lands the shell shows a placeholder
                // that names **this** destination rather than borrowing another's copy, which the
                // first simulator run exposed as a real defect when the Characters tab rendered the
                // Favorites empty state.
                EmptyState(
                    symbol: "person.2.fill",
                    heading: LocalizedCopy.shared.text(for: "nav_characters"),
                    body:
                        LocalizedCopy.shared.text(for: "characters_count")
                            .replacingOccurrences(of: "%1$@", with: "826"),
                    actionLabel: LocalizedCopy.shared.text(for: "action_retry"),
                    action: {}
                )
                .navigationTitle(LocalizedCopy.shared.text(for: destination.labelKey))
            case .episodes, .favorites, .settings:
                placeholder
            }
        }
    }

    private var placeholder: some View {
        EmptyState(
            symbol: symbolName,
            heading: LocalizedCopy.shared.text(for: headingKey),
            body: LocalizedCopy.shared.text(for: bodyKey),
            actionLabel: LocalizedCopy.shared.text(for: "browse_characters"),
            action: { navigation.browseCharacters() }
        )
        .navigationTitle(LocalizedCopy.shared.text(for: destination.labelKey))
    }

    private var headingKey: String {
        destination == .episodes ? "episodes_heading" : "favorites_heading"
    }

    private var bodyKey: String {
        destination == .episodes ? "episodes_body" : "favorites_body"
    }

    private var symbolName: String {
        destination == .episodes ? "play.tv.fill" : "heart"
    }
}

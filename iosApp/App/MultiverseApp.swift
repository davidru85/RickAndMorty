import SwiftUI

/// The iOS app entry point (`TASK-051`).
///
/// It carries the platform integration the features must not own: the window, the single appearance
/// (`UI_SPEC.md` §9) and, from `TASK-054`, the navigation shell. The shared Kotlin framework is
/// linked by the target, so the app is a peer of `:androidApp` rather than a port of it (`DEC-013`).
@main
struct MultiverseApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

import SwiftUI

/// The root the shell replaces in `TASK-054`.
///
/// `TASK-051`'s acceptance is that the target compiles against the shared framework and links
/// exactly one Kotlin framework, so this view states the deployment floor and the single appearance
/// and nothing else. `TASK-054` composes the four destinations here.
struct RootView: View {
    var body: some View {
        Text("Multiverse Explorer")
            .preferredColorScheme(.dark)
    }
}

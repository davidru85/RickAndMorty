import SwiftUI
import UIKit

/// One item the system share sheet offers (`DEC-125`): identifiable, so a `.sheet(item:)` presents it.
struct ShareItem: Identifiable {
    let id = UUID()
    let text: String
}

/// The system share sheet (`UIActivityViewController`), presented by the shell for the Detail's Share
/// (`DEC-125`): the platform's own presentation, with the user choosing the target app.
struct ShareSheet: UIViewControllerRepresentable {
    let item: ShareItem

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [item.text], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

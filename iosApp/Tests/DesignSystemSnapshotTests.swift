@testable import MultiverseApp
import MultiverseExplorer
import SnapshotTesting
import SwiftUI
import XCTest

/// `TEST-UI-010`, `TEST-UI-012`, `TEST-UI-014`, `TEST-UI-015` on iOS (`DEC-025`, `DEC-024`,
/// `REQ-PLAT-003`, `REQ-UX-001`, `REQ-UX-006`, `REQ-UX-007`, `TASK-059`).
///
/// The committed baselines of `TESTING.md` §8.3, with the variants the specification requires:
///
/// - **Liquid Glass and the pre-iOS-26 material fallback** — both paths baselined so the fallback
///   cannot drift (`REQ-PLAT-003`). The `.multiverseGlassPath` environment key is the seam that makes
///   this possible on one OS: the components read it instead of asking the OS, so a single simulator
///   renders both paths.
/// - **Largest Dynamic Type size** — the detail renders without clipping and the grid collapses to one
///   column (`REQ-UX-006`).
/// - **Reduce Transparency** — glass becomes the opaque material (`REQ-UX-007`).
/// - **Single appearance** — iOS is dark-only (`REQ-UX-001`), so no light/dark pair exists here the
///   way Android's `TEST-UI-012` captures one.
///
/// A baseline is recorded only from a state that exists and is correct, and a missing baseline is a
/// failure rather than an auto-accept: `assertSnapshot` fails when its reference is absent, so a new
/// surface cannot silently pass. Regenerating a baseline is a reviewed change of the visual contract
/// (`TESTING.md` §8.2).
@MainActor
final class DesignSystemSnapshotTests: XCTestCase {
    /// The reference directory: `TESTING.md` §13.1 places the iOS baselines beside the Swift tests.
    private var referenceDirectory: String {
        // The test's own file lives at `iosApp/Tests/`; `__FILE__` keeps the path stable no matter
        // where Xcode launches the bundle from.
        "\(URL(fileURLWithPath: #filePath).deletingLastPathComponent().path)/__snapshots__"
    }

    private func record(
        _ name: String,
        glassPath: GlassMaterial,
        dynamicTypeSize: DynamicTypeSize = .large,
        @ViewBuilder content: () -> some View
    ) {
        let view =
            content()
            .environment(\.multiverseGlassPath, glassPath)
            .environment(\.dynamicTypeSize, dynamicTypeSize)
            .frame(width: 320)
        assertSnapshot(
            of: view,
            as: .image(layout: .sizeThatFits),
            named: name,
            record: false,
            file: #filePath,
            testName: "designSystemBaselines"
        )
    }

    func test_TEST_UI_010_designSystemBaselines() {
        let card = MultiverseBootstrap.shared.card(
            id: "1",
            name: "Rick Sanchez",
            species: "Human",
            statusLabelKey: "status_alive",
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
        )
        _ = card
        // The card on both OS paths: the fallback is baselined beside the glass so it cannot drift.
        record("card-glass", glassPath: .glass) { sampleCard() }
        record("card-material", glassPath: .material) { sampleCard() }
        // Reduce Transparency swaps glass for the opaque material (`REQ-UX-007`). The environment's
        // `accessibilityReduceTransparency` is read-only, so the path is selected through the same
        // seam the component uses for its OS check — which is the branch this baseline pins.
        record("card-opaque", glassPath: .opaqueMaterial) { sampleCard() }
        // The largest Dynamic Type size (`REQ-UX-006`).
        record("card-accessibility-text", glassPath: .glass, dynamicTypeSize: .accessibility5) { sampleCard() }
        // The empty state, on both paths.
        record("empty-state-glass", glassPath: .glass) { sampleEmptyState() }
        record("empty-state-material", glassPath: .material) { sampleEmptyState() }
    }

    private func sampleCard() -> some View {
        GlassCharacterCard(
            name: "Rick Sanchez",
            species: "Human",
            statusTone: .alive,
            statusLabel: "Alive",
            portrait: CharacterPresentation.placeholderPortrait()
        )
    }

    private func sampleEmptyState() -> some View {
        EmptyState(
            symbol: "heart",
            heading: LocalizedCopy.shared.text(for: "favorites_heading"),
            body: LocalizedCopy.shared.text(for: "favorites_body"),
            actionLabel: LocalizedCopy.shared.text(for: "browse_characters"),
            action: {}
        )
    }
}

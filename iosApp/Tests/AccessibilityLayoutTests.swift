@testable import MultiverseApp
import SwiftUI
import XCTest

/// `TEST-UI-014` on iOS: at the largest accessibility text size the grid's single column holds a card
/// that fills it, so the text has room instead of truncating (`REQ-UX-006`, `AC-REQ-UX-006-1`,
/// `TASK-059`).
///
/// At the default sizes a card keeps the specified 177 pt width of the two-column grid. At the
/// accessibility sizes the grid drops to one column, and a card that kept 177 pt there clipped its
/// name to "Rick San…" — which the committed baseline had recorded as the expected state.
@MainActor
final class AccessibilityLayoutTests: XCTestCase {
    func test_TEST_UI_014_given_the_largest_text_size_when_a_card_is_in_one_column_then_it_fills_the_column() {
        let size = fittingSize(of: card(), width: 370, dynamicTypeSize: .accessibility5)

        XCTAssertEqual(size.width, 370, accuracy: 0.5, "the one-column card must take the column's width")
    }

    func test_TEST_UI_014_given_the_default_text_size_when_a_card_is_laid_out_then_it_keeps_the_grid_width() {
        let size = fittingSize(of: card(), width: 370, dynamicTypeSize: .large)

        XCTAssertEqual(size.width, MultiverseDimensions.glassCardWidth, accuracy: 0.5)
        XCTAssertEqual(size.height, MultiverseDimensions.glassCardHeight, accuracy: 0.5)
    }

    private func card() -> GlassCharacterCard {
        GlassCharacterCard(
            name: "Rick Sanchez",
            species: "Human",
            statusTone: .alive,
            statusLabel: "Alive",
            portrait: CharacterPresentation.placeholderPortrait()
        )
    }

    private func fittingSize(of view: some View, width: CGFloat, dynamicTypeSize: DynamicTypeSize) -> CGSize {
        let host = UIHostingController(rootView: view.environment(\.dynamicTypeSize, dynamicTypeSize))
        return host.sizeThatFits(in: CGSize(width: width, height: .greatestFiniteMagnitude))
    }
}

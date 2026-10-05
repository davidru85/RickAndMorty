@testable import MultiverseApp
import XCTest

/// `TEST-UNIT-090` — the shell's four tabs as `iOS/Glass tab item`s (`UI_SPEC.md` §4.2, Figma `102:255`,
/// `TASK-114`).
///
/// The tabs were `.tabItem { Text(…) }` with no symbol, and the bar never installed the appearance that
/// keeps an unselected tab white; `GlassTabBar` implemented both and was never used. Each destination
/// now maps to the design system's tab item, so the shell renders the bar through that one component.
@MainActor
final class ShellTabItemsTests: XCTestCase {
    func test_TEST_UNIT_090_given_the_destinations_when_mapped_to_tabs_then_each_carries_its_symbol_in_order() {
        let items = ShellDestination.allCases.map(\.tabItem)

        var symbols: [String] = []
        symbols.append("person.2.fill")
        symbols.append("play.tv.fill")
        symbols.append("heart.fill")
        symbols.append("gearshape.fill")
        XCTAssertEqual(items.map(\.symbol), symbols, "Characters, Episodes, Favorites, Settings, each with its §4.2 symbol")
        XCTAssertEqual(items.map(\.id), ShellDestination.allCases.map(\.id), "a tab's id is its destination's")
        XCTAssertEqual(
            items.map(\.label),
            ShellDestination.allCases.map { LocalizedCopy.shared.text(for: $0.labelKey) },
            "a tab's label is its destination's copy"
        )
    }

    func test_TEST_UNIT_090_given_a_tab_id_when_resolved_then_it_selects_its_destination() {
        for destination in ShellDestination.allCases {
            XCTAssertEqual(ShellDestination(tabID: destination.tabItem.id), destination)
        }
        XCTAssertNil(ShellDestination(tabID: "locations"), "an id that names no destination selects nothing")
    }
}

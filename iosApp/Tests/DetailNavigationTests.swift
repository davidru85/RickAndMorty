@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// Opening a card pushes its detail (`REQ-FUNC-002`, `AC-REQ-FUNC-002-1`, `TASK-054`/`TASK-055`).
///
/// The shell hands the tapped card to the destination's `navigationDestination`, and SwiftUI honours
/// that modifier only **inside** a `NavigationStack`; outside it the push is dropped and the runtime
/// logs a fault. The case hosts a real destination in a window scene, opens a card, and requires the
/// detail to appear. The Episodes destination is used because it starts no request, and the detail is
/// a probe, so no graph or network is involved.
@MainActor
final class DetailNavigationTests: XCTestCase {
    func test_TEST_UI_002_given_a_destination_when_a_card_is_opened_then_its_detail_is_pushed() throws {
        let probe = DetailProbe()
        let driver = OpenedDriver()
        let host = UIHostingController(
            rootView: DrivenDestination(driver: driver, probe: probe)
        )
        let scene = try XCTUnwrap(
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first,
            "the test host must provide a window scene"
        )
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 402, height: 874)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        settle()

        driver.opened = CharacterCardUi(
            id: "1",
            name: "Rick Sanchez",
            species: DisplayTextData(value: "Human"),
            status: CharacterStatusAlive.shared,
            statusLabel: CopyKeys.shared.STATUS_ALIVE,
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
        )
        settle()

        XCTAssertEqual(probe.shownIdentifiers, ["1"], "opening a card must push that card's detail")
    }

    private func settle() {
        RunLoop.main.run(until: Date().addingTimeInterval(0.5))
    }
}

/// Records which card's detail appeared.
@MainActor
private final class DetailProbe {
    private(set) var shownIdentifiers: [String] = []

    func shown(_ card: CharacterCardUi) {
        shownIdentifiers.append(CharacterPresentation.identifier(card.id))
    }
}

/// The opened card, held the way the shell holds it.
@MainActor
private final class OpenedDriver: ObservableObject {
    @Published var opened: CharacterCardUi?
}

private struct DrivenDestination: View {
    @ObservedObject var driver: OpenedDriver
    let probe: DetailProbe

    var body: some View {
        DestinationView(
            destination: .episodes,
            navigation: ShellNavigation(selected: .episodes),
            opened: $driver.opened,
            detail: { card in
                Color.clear.onAppear { probe.shown(card) }
            }
        )
    }
}

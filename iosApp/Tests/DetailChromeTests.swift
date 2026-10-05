@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import UIKit
import XCTest

/// `TEST-UI-039` — the Detail without the system chrome (`UI_SPEC.md` §6.3, Figma `26:452`, `DEC-140`,
/// `TASK-114`).
///
/// The tab bar and the navigation bar stayed visible over the pushed Detail, which Figma draws with
/// neither. Hiding the navigation bar must not take the edge-swipe back gesture with it, because the
/// glass Back is then the only other way out (`REQ-UX-003`). The shell's real destination is hosted in
/// a tab view, with a probe for the detail, so no graph or network is involved.
@MainActor
final class DetailChromeTests: XCTestCase {
    func test_TEST_UI_039_given_an_opened_card_when_shown_then_the_bars_hide_and_swipe_back_works() throws {
        let driver = ChromeDriver()
        let host = UIHostingController(rootView: ChromeHarness(driver: driver))
        // Whether the push is the zoom or, with Reduce Motion, the system's own: the edge swipe is
        // asserted on whichever path the simulator's setting selects.
        // Run alone, the case can start before the host app has connected its scene; wait for it.
        let deadline = Date().addingTimeInterval(5)
        while UIApplication.shared.connectedScenes.isEmpty && Date() < deadline { settle() }
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

        let tabs = try XCTUnwrap(Self.find(UITabBarController.self, from: host), "the shell is a tab view")
        let stack = try XCTUnwrap(Self.find(UINavigationController.self, from: host), "a destination is a stack")
        XCTAssertFalse(tabs.tabBar.isHidden, "the tab bar shows on a top-level screen")

        driver.opened = MultiverseBootstrap.shared.card(
            id: "1",
            name: "Rick Sanchez",
            species: "Human",
            statusLabelKey: "status_alive",
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
        )
        settle()

        XCTAssertEqual(stack.viewControllers.count, 2, "the detail is pushed")
        XCTAssertTrue(tabs.tabBar.isHidden, "Figma 26:452 draws no tab bar over the Detail")
        XCTAssertTrue(stack.isNavigationBarHidden, "Figma 26:452 draws no navigation bar over the Detail")
        let swipe = try XCTUnwrap(stack.interactivePopGestureRecognizer)
        XCTAssertTrue(swipe.isEnabled, "the edge swipe stays enabled")
        XCTAssertNotEqual(
            swipe.delegate?.gestureRecognizerShouldBegin?(swipe),
            false,
            "the edge swipe may begin on the Detail, so hiding the bar removes no way back"
        )
    }

    private func settle() {
        RunLoop.main.run(until: Date().addingTimeInterval(0.8))
    }

    private static func find<T: UIViewController>(_ type: T.Type, from root: UIViewController) -> T? {
        if let match = root as? T { return match }
        for child in root.children {
            if let match = find(type, from: child) { return match }
        }
        return nil
    }
}

/// The opened card, held the way the shell holds it.
@MainActor
private final class ChromeDriver: ObservableObject {
    @Published var opened: CharacterCardUi?
}

private struct ChromeHarness: View {
    @ObservedObject var driver: ChromeDriver

    var body: some View {
        TabView {
            Tab("Episodes", systemImage: "play.tv.fill") {
                DestinationView(
                    destination: .episodes,
                    navigation: ShellNavigation(selected: .episodes),
                    opened: $driver.opened,
                    detail: { _ in Color.clear }
                )
            }
        }
    }
}

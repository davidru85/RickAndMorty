@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-UI-048` — the iOS list asks for the next page as the user scrolls towards its end, and only
/// then (`TASK-136`, `GAP-046`, `AC-REQ-FUNC-001-1`, `AC-REQ-FUNC-001-2`, `API_SPECS.md` §8).
///
/// The paging trigger sat in the non-lazy stack around the grid: it appeared with the first render, so
/// page 2 was requested at launch, and it never appeared again, so no later page ever was. The case hosts
/// the real screen in a window, scrolls its scroll view the way a finger does, and counts the intents.
@MainActor
final class DiscoveryPagingTests: XCTestCase {
    func test_TEST_UI_048_given_a_first_page_when_each_end_is_reached_then_each_end_requests_a_page() throws {
        let host = try PagingHost(state: state(cards: 20))

        XCTAssertEqual(host.nextPageRequests, 0, "TEST-UI-048: no page is requested before the user scrolls")

        host.scrollToBottom()
        let afterFirstEnd = host.nextPageRequests
        XCTAssertGreaterThan(afterFirstEnd, 0, "TEST-UI-048: reaching the end of page 1 requests the next page")

        host.update(state(cards: 40))
        host.scrollToBottom()
        XCTAssertGreaterThan(
            host.nextPageRequests,
            afterFirstEnd,
            "TEST-UI-048: reaching the end again after page 2 arrived requests another page"
        )
    }

    // MARK: - States

    private func state(cards count: Int) -> CharacterListUiState {
        CharacterListUiState(
            filter: CharacterFilter(query: "", status: StatusFilter.all),
            items: (1...count).map { card(id: "\($0)") },
            totalCount: KotlinInt(int: 826),
            loadState: LoadStateContent.shared,
            isAppending: false,
            isStale: false,
            contentFailure: nil,
            isRefreshing: false
        )
    }

    private func card(id: String) -> CharacterCardUi {
        CharacterCardUi(
            id: id,
            name: "Character \(id)",
            species: DisplayTextData(value: "Human"),
            status: CharacterStatusAlive.shared,
            statusLabel: CopyKeys.shared.STATUS_ALIVE,
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/\(id).jpeg"
        )
    }
}

/// The real Discovery screen hosted in a window of the test host's scene, with its intents recorded.
@MainActor
private final class PagingHost {
    private let driver: PagingDriver
    private let window: UIWindow

    var nextPageRequests: Int { driver.intents.filter { $0 is CharacterListIntentLoadNextPage }.count }

    init(state: CharacterListUiState) throws {
        let scene = try XCTUnwrap(
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first,
            "the test host must provide a window scene"
        )
        driver = PagingDriver(state: state)
        window = UIWindow(windowScene: scene)
        window.frame = HostedRendering.canvas
        window.rootViewController = UIHostingController(rootView: PagingScreen(driver: driver))
        window.makeKeyAndVisible()
        settle()
    }

    deinit {
        MainActor.assumeIsolated { window.isHidden = true }
    }

    func update(_ state: CharacterListUiState) {
        driver.state = state
        settle()
    }

    /// Scrolls the screen's scroll view to its end in steps, as a fling would pass through the rows.
    func scrollToBottom() {
        guard let scrollView = Self.scrollView(in: window) else {
            XCTFail("the Discovery screen hosts a scroll view")
            return
        }
        for _ in 0..<Self.scrollSteps {
            let inset = scrollView.adjustedContentInset.bottom
            let bottom = scrollView.contentSize.height + inset - scrollView.bounds.height
            let next = min(bottom, scrollView.contentOffset.y + scrollView.bounds.height)
            scrollView.setContentOffset(CGPoint(x: 0, y: max(next, 0)), animated: false)
            settle()
            if next >= bottom { break }
        }
    }

    private func settle() {
        window.rootViewController?.view.setNeedsLayout()
        window.rootViewController?.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.3))
    }

    private static func scrollView(in view: UIView) -> UIScrollView? {
        if let scrollView = view as? UIScrollView, scrollView.contentSize.height > scrollView.bounds.height {
            return scrollView
        }
        return view.subviews.lazy.compactMap { scrollView(in: $0) }.first
    }

    /// Enough viewport-sized steps to cross two pages of cards.
    private static let scrollSteps = 40
}

@MainActor
private final class PagingDriver: ObservableObject {
    @Published var state: CharacterListUiState
    private(set) var intents: [any CharacterListIntent] = []

    init(state: CharacterListUiState) {
        self.state = state
    }

    func record(_ intent: any CharacterListIntent) {
        intents.append(intent)
    }
}

private struct PagingScreen: View {
    @ObservedObject var driver: PagingDriver

    var body: some View {
        DiscoveryScreen(
            state: driver.state,
            loader: PortraitImageStub(),
            onIntent: { driver.record($0) },
            onOpenDetail: { _ in }
        )
        .background(MultiverseBrandColors.spaceBlack)
        .environment(\.multiverseGlassPath, .material)
    }
}

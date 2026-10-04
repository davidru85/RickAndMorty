@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// The Discovery grid paints its cards after the skeleton (`TASK-055`, `ERROR_FLOW.md` §8, `GAP-031`).
///
/// The running app loaded page 1 — the count line read 826 — and still painted the six skeleton
/// cards. A render from a single state cannot see that, because each state alone paints correctly; the
/// defect is in the **transition**. So the case hosts the real screen in a window, moves one state
/// holder from `Loading` to `Content` the way the shared reducer does, and requires the result to
/// paint the same card names as a screen that started on that `Content`.
@MainActor
final class DiscoveryGridTransitionTests: XCTestCase {
    func test_GAP_031_given_the_skeleton_when_content_arrives_then_the_cards_paint_as_on_a_fresh_render() throws {
        let content = state(
            items: (1...6).map { card(id: "\($0)", name: "Rick Sanchez \($0)") },
            loadState: LoadStateContent.shared
        )

        let fresh = try brightPixels(rendering: [content])
        let transitioned = try brightPixels(rendering: [state(items: [], loadState: LoadStateLoading.shared), content])

        XCTAssertGreaterThan(fresh, 0, "a fresh content render paints its card names")
        XCTAssertEqual(
            transitioned,
            fresh,
            "after Loading → Content the grid must paint the cards, not keep the skeleton cells"
        )
    }

    // MARK: - Hosting

    /// Renders the screen through each state in turn, in a real window, and counts the near-white
    /// pixels of the final frame: the card names are drawn in `MultiverseLabelColors.primary`, and a
    /// skeleton card draws none.
    private func brightPixels(rendering states: [CharacterListUiState]) throws -> Int {
        let driver = StateDriver(state: try XCTUnwrap(states.first))
        let host = UIHostingController(rootView: DrivenDiscoveryScreen(driver: driver))
        // A window draws only inside a scene; the test host app provides one.
        let scene = try XCTUnwrap(
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first,
            "the test host must provide a window scene"
        )
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 402, height: 874)
        window.rootViewController = host
        window.makeKeyAndVisible()
        settle(host)
        for next in states.dropFirst() {
            driver.state = next
            settle(host)
        }
        let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
            _ = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        window.isHidden = true
        return try countNearWhitePixels(in: image)
    }

    private func settle(_ host: UIViewController) {
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.3))
    }

    private func countNearWhitePixels(in image: UIImage) throws -> Int {
        let cgImage = try XCTUnwrap(image.cgImage)
        let width = cgImage.width
        let height = cgImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let context = try XCTUnwrap(
            CGContext(
                data: &pixels,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
        )
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        var bright = 0
        for index in stride(from: 0, to: pixels.count, by: 4)
        where pixels[index] > 200 && pixels[index + 1] > 200 && pixels[index + 2] > 200 {
            bright += 1
        }
        return bright
    }

    // MARK: - States

    private func state(items: [CharacterCardUi], loadState: any LoadState) -> CharacterListUiState {
        CharacterListUiState(
            filter: CharacterFilter(query: "", status: StatusFilter.all),
            items: items,
            totalCount: KotlinInt(int: 826),
            loadState: loadState,
            isAppending: false,
            isStale: false
        )
    }

    private func card(id: String, name: String) -> CharacterCardUi {
        CharacterCardUi(
            id: id,
            name: name,
            species: DisplayTextData(value: "Human"),
            status: CharacterStatusAlive.shared,
            statusLabel: CopyKeys.shared.STATUS_ALIVE,
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/\(id).jpeg"
        )
    }
}

/// Holds the state the hosted screen renders, as a state holder does.
@MainActor
private final class StateDriver: ObservableObject {
    @Published var state: CharacterListUiState

    init(state: CharacterListUiState) {
        self.state = state
    }
}

private struct DrivenDiscoveryScreen: View {
    @ObservedObject var driver: StateDriver

    var body: some View {
        DiscoveryScreen(state: driver.state, loader: PortraitImageStub(), onIntent: { _ in }, onOpenDetail: { _ in })
            .background(MultiverseBrandColors.spaceBlack)
            .environment(\.multiverseGlassPath, .material)
    }
}

@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-UI-047` — the Discovery count line keeps its space while the count is unknown (`TASK-135`,
/// `GAP-045`, `AC-REQ-FUNC-001-3`, `UI_SPEC.md` §6.2).
///
/// A filter change resets the total until the new page answers, and the screen removed the line for
/// that time, so the search field, the segments and the grid moved up one line and back. The case hosts
/// the real screen in a window, moves one state holder from a known count to an unknown one the way the
/// shared reducer does, and measures how far the content below the line moved: it aligns the two frames'
/// horizontal-edge profiles, so a background gradient that never moves cannot hide a shift.
@MainActor
final class DiscoveryCountLineTests: XCTestCase {
    func test_TEST_UI_047_given_a_known_count_when_it_becomes_unknown_then_the_content_below_keeps_its_place() throws {
        let frames = try renderFrames(states: [state(totalCount: 826), state(totalCount: nil)])

        let shift = try verticalShift(from: frames[0], to: frames[1])

        XCTAssertEqual(
            shift,
            0,
            "TEST-UI-047: the content below the count line moved \(shift) pt while the count was unknown"
        )
    }

    // MARK: - Hosting

    /// Hosts the screen in a window and returns one frame per state, each after the screen settled.
    private func renderFrames(states: [CharacterListUiState]) throws -> [UIImage] {
        try hosted(states: states) { window in
            UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                _ = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
        }
    }

    private func hosted<Output>(states: [CharacterListUiState], capture: (UIWindow) -> Output) throws -> [Output] {
        let scene = try XCTUnwrap(
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first,
            "the test host must provide a window scene"
        )
        let driver = CountLineDriver(state: try XCTUnwrap(states.first))
        let window = UIWindow(windowScene: scene)
        window.frame = HostedRendering.canvas
        window.rootViewController = UIHostingController(rootView: CountLineScreen(driver: driver))
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        var outputs: [Output] = []
        for (index, next) in states.enumerated() {
            if index > 0 { driver.state = next }
            window.rootViewController?.view.setNeedsLayout()
            window.rootViewController?.view.layoutIfNeeded()
            // Long enough for the line's fade to finish, so a frame shows the settled layout.
            RunLoop.main.run(until: Date().addingTimeInterval(0.6))
            outputs.append(capture(window))
        }
        return outputs
    }

    // MARK: - Measuring

    /// The vertical shift, in points, that best aligns the second frame's content with the first's,
    /// over the band below the count line (the search field, the segments and the skeleton grid).
    private func verticalShift(from first: UIImage, to second: UIImage) throws -> Int {
        let scale = first.scale
        let firstProfile = try edgeProfile(of: first)
        let secondProfile = try edgeProfile(of: second)
        let bandStart = Int(Self.bandTop * scale)
        let bandEnd = min(firstProfile.count, secondProfile.count) - Int(Self.maximumShift * scale) - 1
        var best = (shift: 0, error: Double.greatestFiniteMagnitude)
        for shift in -Int(Self.maximumShift * scale)...Int(Self.maximumShift * scale) {
            var error = 0.0
            for row in bandStart..<bandEnd {
                error += abs(firstProfile[row] - secondProfile[row + shift])
            }
            if error < best.error { best = (shift, error) }
        }
        return Int((Double(best.shift) / scale).rounded())
    }

    /// Per row, the summed horizontal luminance change: edges and text, not the smooth background.
    private func edgeProfile(of image: UIImage) throws -> [Double] {
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
        return (0..<height).map { row in
            var energy = 0.0
            for column in 1..<width {
                let current = luminance(pixels, row: row, column: column, width: width)
                let previous = luminance(pixels, row: row, column: column - 1, width: width)
                energy += abs(current - previous)
            }
            return energy
        }
    }

    private func luminance(_ pixels: [UInt8], row: Int, column: Int, width: Int) -> Double {
        let offset = (row * width + column) * 4
        return 0.299 * Double(pixels[offset]) + 0.587 * Double(pixels[offset + 1]) + 0.114 * Double(pixels[offset + 2])
    }

    /// Below the count line: the band starts under the line's own pixels, which fade by design.
    private static let bandTop: CGFloat = 140

    /// Larger than one line of the count's text at any size the case renders.
    private static let maximumShift: CGFloat = 48

    // MARK: - States

    private func state(totalCount: Int32?) -> CharacterListUiState {
        CharacterListUiState(
            filter: CharacterFilter(query: "", status: StatusFilter.all),
            items: [],
            totalCount: totalCount.map { KotlinInt(int: $0) },
            loadState: LoadStateLoading.shared,
            isAppending: false,
            isStale: false,
            contentFailure: nil,
            isRefreshing: false
        )
    }
}

/// Holds the state the hosted screen renders, as a state holder does.
@MainActor
private final class CountLineDriver: ObservableObject {
    @Published var state: CharacterListUiState

    init(state: CharacterListUiState) {
        self.state = state
    }
}

private struct CountLineScreen: View {
    @ObservedObject var driver: CountLineDriver

    var body: some View {
        DiscoveryScreen(state: driver.state, loader: PortraitImageStub(), onIntent: { _ in }, onOpenDetail: { _ in })
            .background(MultiverseBrandColors.spaceBlack)
            .environment(\.multiverseGlassPath, .material)
    }
}

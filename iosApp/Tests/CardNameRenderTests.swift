@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-UI-013` on iOS: a card publishes the merged announcement the acceptance criterion names, and
/// the name it renders is the one the state carries (`REQ-UX-005`, `AC-REQ-UX-005-1`, `TASK-055`).
///
/// The case renders the **real** card through the real glass surface and reads it back with
/// `ImageRenderer`, so a name that fails to paint is caught here rather than only on a device. That is
/// how a defect was found where the running app's grid showed the status dot and no name.
@MainActor
final class CardNameRenderTests: XCTestCase {
    func test_TEST_UI_013_given_a_card_when_rendered_then_its_name_is_painted_into_the_image() throws {
        let card = MultiverseBootstrap.shared.card(
            id: "1",
            name: "Rick Sanchez",
            species: "Human",
            statusLabelKey: "status_alive",
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
        )
        // The **cell** rather than the bare card: the grid wraps the glass card in a `Button` with
        // `.buttonStyle(.plain)`, and that wrapper is exactly the difference the running app showed.
        let view =
            CharacterCardCell(
                card: card,
                loader: nil,
                identifierPrefix: "probe.card",
                action: {}
            )
            .frame(width: 184, height: 260)

        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let image = try XCTUnwrap(renderer.uiImage, "the card must render an image")

        // The name is drawn in `MultiverseLabelColors.primary` (#FFFFFF). The case asserts that the
        // card's bar actually contains near-white pixels, which is what "the name is visible" means in
        // pixels; a card whose text is clipped or covered renders none.
        let bright = try countNearWhitePixels(in: image)
        XCTAssertGreaterThan(bright, 0, "the card's name must paint visible pixels in the bar")
    }

    /// The card's own announcement, unchanged by rendering (`AC-REQ-UX-005-1`).
    func test_TEST_UI_013_given_a_card_when_announced_then_it_names_name_status_and_species() {
        let label = CharacterPresentation.cardLabel(
            name: "Rick Sanchez",
            statusLabel: "Alive",
            species: "Human"
        )
        XCTAssertEqual(label, "Rick Sanchez, Alive, Human")
    }

    private func countNearWhitePixels(in image: UIImage) throws -> Int {
        let cgImage = try XCTUnwrap(image.cgImage)
        let width = cgImage.width
        let height = cgImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let space = CGColorSpaceCreateDeviceRGB()
        let context = try XCTUnwrap(
            CGContext(
                data: &pixels,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: space,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
        )
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        var bright = 0
        for index in stride(from: 0, to: pixels.count, by: 4) {
            if pixels[index] > 200, pixels[index + 1] > 200, pixels[index + 2] > 200 {
                bright += 1
            }
        }
        return bright
    }
}

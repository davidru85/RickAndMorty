import SwiftUI
import XCTest

/// Renders a SwiftUI hierarchy the way the device does: in a window of the test host's scene, laid out
/// and drawn with its real screen updates. `ImageRenderer` does not lay out lazy containers or run a
/// state change, so the cases that are about what a screen actually paints use this instead.
@MainActor
enum HostedRendering {
    /// The phone-sized canvas the screen cases render on, in points.
    static let canvas = CGRect(x: 0, y: 0, width: 402, height: 874)

    /// Hosts `controller` in a window, runs `steps` between frames, and returns the final frame.
    static func render(_ controller: UIViewController, steps: [() -> Void] = []) throws -> UIImage {
        let scene = try XCTUnwrap(
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first,
            "the test host must provide a window scene"
        )
        let window = UIWindow(windowScene: scene)
        window.frame = canvas
        window.rootViewController = controller
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        settle(controller)
        for step in steps {
            step()
            settle(controller)
        }
        let rendered = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
            _ = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        // The frame is compared in the encoding its reference was stored in. The renderer's in-memory
        // bitmap format follows the host's GPU (bit depth, range), so two hosts whose PNGs agree to one
        // level still compared as different images; round-tripping through PNG removes that.
        let encoded = try XCTUnwrap(rendered.pngData(), "the rendered frame must encode as PNG")
        return try XCTUnwrap(UIImage(data: encoded, scale: rendered.scale), "the encoded frame must decode")
    }

    /// The near-white pixels of `image`: the label colour of names and titles
    /// (`MultiverseLabelColors.primary`), which a skeleton, a placeholder or a portrait does not paint.
    static func nearWhitePixels(in image: UIImage) throws -> Int {
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

    private static func settle(_ controller: UIViewController) {
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.3))
    }
}

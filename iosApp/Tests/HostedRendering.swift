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

    /// The 8-bit colour of `image` at `point`, in points, so a case can name the spot it samples in the
    /// geometry the specification uses rather than in pixels of a given scale.
    static func color(in image: UIImage, at point: CGPoint) throws -> (red: Int, green: Int, blue: Int) {
        let cgImage = try XCTUnwrap(image.cgImage)
        let x = Int(point.x * image.scale)
        let y = Int(point.y * image.scale)
        var pixel = [UInt8](repeating: 0, count: 4)
        let context = try XCTUnwrap(
            CGContext(
                data: &pixel,
                width: 1,
                height: 1,
                bitsPerComponent: 8,
                bytesPerRow: 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
        )
        // Draw the image shifted so the sampled pixel lands on the one-pixel context's origin.
        context.draw(
            cgImage,
            in: CGRect(x: -x, y: y - cgImage.height + 1, width: cgImage.width, height: cgImage.height)
        )
        return (Int(pixel[0]), Int(pixel[1]), Int(pixel[2]))
    }

    /// The bounds, in points, of the pixels of `image` inside `region` (points; the whole image when
    /// `nil`) that `matches` accepts, with how many there are; `nil` bounds when none does.
    static func matchingPixels(
        in image: UIImage,
        region: CGRect? = nil,
        matches: (_ red: Int, _ green: Int, _ blue: Int) -> Bool
    ) throws -> (count: Int, bounds: CGRect?) {
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
        let scale = image.scale
        let area = region.map {
            CGRect(x: $0.minX * scale, y: $0.minY * scale, width: $0.width * scale, height: $0.height * scale)
        } ?? CGRect(x: 0, y: 0, width: width, height: height)
        var count = 0
        var minX = Int.max
        var minY = Int.max
        var maxX = Int.min
        var maxY = Int.min
        for y in max(0, Int(area.minY))..<min(height, Int(area.maxY)) {
            for x in max(0, Int(area.minX))..<min(width, Int(area.maxX)) {
                let index = (y * width + x) * 4
                guard matches(Int(pixels[index]), Int(pixels[index + 1]), Int(pixels[index + 2])) else { continue }
                count += 1
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
            }
        }
        guard count > 0 else { return (0, nil) }
        let bounds = CGRect(
            x: CGFloat(minX) / scale,
            y: CGFloat(minY) / scale,
            width: CGFloat(maxX - minX + 1) / scale,
            height: CGFloat(maxY - minY + 1) / scale
        )
        return (count, bounds)
    }

    private static func settle(_ controller: UIViewController) {
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.3))
    }
}

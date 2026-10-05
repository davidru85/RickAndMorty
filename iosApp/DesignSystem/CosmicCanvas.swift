import SwiftUI

/// The **cosmic canvas** every iOS screen draws on (`UI_SPEC.md` §3.2, Figma `29:383`/`29:385` on the
/// top-level screens, `26:456`/`26:458` on the Detail): Space Black under two soft radial glows, one
/// Cosmic Violet and one Portal Green, fixed behind the scrolling content.
///
/// Each glow is the Figma ellipse's radial fill — the colour at its peak opacity in the centre, 45 % of
/// that at 45 % of the radius, clear at the rim — placed from the edge Figma anchors it to, so a wider
/// or taller screen keeps each glow in the same corner. The canvas ignores the safe area: it is the
/// backdrop, and the content sets its own insets.
public struct CosmicCanvas: View {
    /// Where the two glows sit.
    public enum Layout: Sendable {
        /// The four top-level screens: violet over the top-leading corner, green off the trailing edge.
        case topLevel
        /// The Detail: both glows low, behind the frosted panel.
        case detail
    }

    private let layout: Layout
    private let base: Bool

    /// - Parameters:
    ///   - layout: where the glows sit.
    ///   - base: whether Space Black is painted under them; the Detail lays its glows over its own
    ///     blurred-portrait backdrop instead.
    public init(_ layout: Layout = .topLevel, base: Bool = true) {
        self.layout = layout
        self.base = base
    }

    public var body: some View {
        Canvas { context, size in
            if base {
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(MultiverseBrandColors.spaceBlack))
            }
            for glow in glows(in: size) {
                glow.draw(in: &context)
            }
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }

    private func glows(in size: CGSize) -> [Glow] {
        var glows: [Glow] = []
        switch layout {
        case .topLevel:
            // `29:383`: 620 pt at (-260, -220); `29:385`: 520 pt at (170, 120) on the 402 pt frame,
            // whose centre is 28 pt past the trailing edge.
            glows.append(
                Glow(color: MultiverseBrandColors.cosmicViolet, peak: 0.6, center: CGPoint(x: 50, y: 90), radius: 310)
            )
            glows.append(
                Glow(
                    color: MultiverseBrandColors.portalGreen,
                    peak: 0.35,
                    center: CGPoint(x: size.width + 28, y: 380),
                    radius: 260
                )
            )
        case .detail:
            // `26:456`: 520 pt at (-220, 380); `26:458`: 460 pt at (180, 600) on the 874 pt frame,
            // anchored to the bottom so the glows stay behind the panel.
            glows.append(
                Glow(
                    color: MultiverseBrandColors.cosmicViolet,
                    peak: 0.45,
                    center: CGPoint(x: 40, y: size.height - 234),
                    radius: 260
                )
            )
            glows.append(
                Glow(
                    color: MultiverseBrandColors.portalGreen,
                    peak: 0.3,
                    center: CGPoint(x: size.width + 8, y: size.height - 44),
                    radius: 230
                )
            )
        }
        return glows
    }

    /// One radial glow of the canvas.
    private struct Glow {
        let color: Color
        let peak: Double
        let center: CGPoint
        let radius: CGFloat

        func draw(in context: inout GraphicsContext) {
            var stops: [Gradient.Stop] = []
            stops.append(.init(color: color.opacity(peak), location: 0))
            stops.append(.init(color: color.opacity(peak * 0.45), location: 0.45))
            stops.append(.init(color: color.opacity(0), location: 1))
            let bounds = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
            context.fill(
                Path(ellipseIn: bounds),
                with: .radialGradient(Gradient(stops: stops), center: center, startRadius: 0, endRadius: radius)
            )
        }
    }
}

extension View {
    /// Draws this screen over the cosmic canvas (`UI_SPEC.md` §3.2).
    public func cosmicCanvas(_ layout: CosmicCanvas.Layout = .topLevel) -> some View {
        background { CosmicCanvas(layout) }
    }
}

#Preview("Cosmic canvas — top level and detail") {
    HStack(spacing: 0) {
        CosmicCanvas(.topLevel)
        CosmicCanvas(.detail)
    }
    .preferredColorScheme(.dark)
}

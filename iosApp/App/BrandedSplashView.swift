import SwiftUI

/// The branded splash (`UI_SPEC.md` §6.1, Figma `29:291`, `REQ-FUNC-007`, `TASK-007`/`TASK-054`).
///
/// Space Black with three aurora orbs — Cosmic Violet, Nebula Violet and Portal Green — drifting on a
/// 20 s ease-in-out loop over a fixed starfield; the portal logo turning behind a 212 pt Liquid Glass
/// lens; then the wordmark: "Multiverse" in the editorial display, "EXPLORER" in Caption 2 in Portal
/// Green with +7 tracking, and the tagline.
///
/// The portal's rotation **is** the loading indicator, so it is exposed as an indeterminate progress
/// indicator labelled with the canonical `splash_loading` copy — the same signal the Android splash
/// exposes (`TEST-A11Y-001`, `TESTING.md` §9). It follows the curve both platforms share
/// ([PortalSpin]); with Reduce Motion on, the portal pulses its opacity instead, the orbs hold still,
/// and the progress signal stays (`AC-REQ-UX-007-1`).
struct BrandedSplashView: View {
    let reduceMotion: Bool

    /// When the splash appeared: the rotation, the pulse and the drift are functions of the time since.
    @State private var start = Date()

    /// The editorial display scales with Dynamic Type (`UI_SPEC.md` §3.4).
    @ScaledMetric(relativeTo: .largeTitle) private var wordmarkSize = MultiverseType.editorialDisplaySize

    var body: some View {
        TimelineView(.animation) { timeline in
            let elapsed = timeline.date.timeIntervalSince(start)
            ZStack {
                SplashSky(drift: reduceMotion ? 0 : SplashSky.drift(elapsed: elapsed))
                VStack(spacing: SplashLayout.lensToWordmark) {
                    lens(elapsed: elapsed)
                    wordmark
                }
            }
        }
        .accessibilityElement()
        .accessibilityLabel(LocalizedCopy.shared.text(for: "splash_loading"))
        .accessibilityAddTraits(.updatesFrequently)
    }

    /// The portal logo turning behind the clear glass lens, which refracts it (`UI_SPEC.md` §6.1).
    private func lens(elapsed: TimeInterval) -> some View {
        ZStack {
            PortalLogo()
                .frame(width: SplashLayout.logo, height: SplashLayout.logo)
                .rotationEffect(.degrees(reduceMotion ? 0 : PortalSpin.angle(elapsed: elapsed)))
                .opacity(reduceMotion ? PortalSpin.pulse(elapsed: elapsed) : 1)
            Color.clear
                .frame(width: SplashLayout.lens, height: SplashLayout.lens)
                .glassSurface(.rounded(SplashLayout.lensCorner), variant: .clear)
        }
    }

    private var wordmark: some View {
        VStack(spacing: MultiverseDimensions.spaceS) {
            Text(LocalizedCopy.shared.text(for: "splash_wordmark"))
                .font(MultiverseType.editorialDisplay(size: wordmarkSize))
                .foregroundStyle(MultiverseLabelColors.primary)
            Text(LocalizedCopy.shared.text(for: "splash_wordmark_sub"))
                .font(MultiverseType.caption2Emphasized)
                .tracking(SplashLayout.subTracking)
                .foregroundStyle(MultiverseBrandColors.portalGreen)
            Text(LocalizedCopy.shared.text(for: "splash_tagline"))
                .font(MultiverseType.subheadline)
                .foregroundStyle(MultiverseLabelColors.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, MultiverseDimensions.spaceL)
    }
}

/// The splash's fixed geometry (Figma `29:291`, `UI_SPEC.md` §6.1).
enum SplashLayout {
    /// The portal logo's size.
    static let logo: CGFloat = 176
    /// The glass lens's side and its continuous corner.
    static let lens: CGFloat = 212
    static let lensCorner: CGFloat = 60
    /// The gap between the lens and the wordmark.
    static let lensToWordmark: CGFloat = 56
    /// The "EXPLORER" tracking.
    static let subTracking: CGFloat = 7
}

/// The portal's motion (`UI_SPEC.md` §7), the curve Android's `portalAngleAt` follows (`TEST-UNIT-086`):
/// clockwise, 0° → 360° over 1.2 s on the ease-in cubic, then a constant 900°/s for as long as the
/// splash stays. The ease-in ends at the constant speed, so the hand-over shows no jump, and nothing
/// restarts, so the portal never stutters.
enum PortalSpin {
    /// The acceleration's duration.
    static let accelerationSeconds: Double = 1.2

    /// The constant speed after it, clockwise.
    static let constantDegreesPerSecond: Double = 900

    /// The Reduce Motion pulse's half-period and its lowest opacity: 0.6 ↔ 1 over 1.2 s.
    static let pulseSeconds: Double = 1.2
    static let pulseLow: Double = 0.6

    /// `cubic-bezier(0.32, 0, 0.67, 0)` of `UI_SPEC.md` §7.
    private static let easeIn = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.32, y: 0),
        endControlPoint: UnitPoint(x: 0.67, y: 0)
    )

    /// The angle in degrees, clockwise (positive in SwiftUI), [elapsed] seconds after the splash appeared.
    static func angle(elapsed: TimeInterval) -> Double {
        if elapsed < accelerationSeconds {
            return 360 * easeIn.value(at: max(elapsed, 0) / accelerationSeconds)
        }
        return 360 + constantDegreesPerSecond * (elapsed - accelerationSeconds)
    }

    /// The Reduce Motion opacity [elapsed] seconds after the splash appeared.
    static func pulse(elapsed: TimeInterval) -> Double {
        let cycle = (max(elapsed, 0) / pulseSeconds).truncatingRemainder(dividingBy: 2)
        let fraction = cycle <= 1 ? cycle : 2 - cycle
        return pulseLow + (1 - pulseLow) * fraction
    }
}

/// The splash's sky (Figma `29:291`): Space Black, the three aurora orbs and the starfield.
private struct SplashSky: View {
    /// How far into its drift loop the sky is, 0…1.
    let drift: Double

    /// The 20 s ease-in-out drift loop: out and back, so the orbs never jump.
    static func drift(elapsed: TimeInterval) -> Double {
        (1 - cos(2 * .pi * elapsed / driftSeconds)) / 2
    }

    private static let driftSeconds: Double = 20

    var body: some View {
        Canvas { context, size in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(MultiverseBrandColors.spaceBlack))
            for orb in orbs(in: size) {
                orb.draw(in: &context)
            }
            drawStars(in: &context, size: size)
        }
        .ignoresSafeArea()
    }

    /// The orbs, placed and sized from the Figma export's measured falloff, each drifting its own way.
    private func orbs(in size: CGSize) -> [Orb] {
        var orbs: [Orb] = []
        orbs.append(
            Orb(
                color: MultiverseBrandColors.cosmicViolet,
                peak: 0.75,
                center: CGPoint(x: 165 + 20 * drift, y: 215 + 12 * drift),
                radius: 360
            )
        )
        orbs.append(
            Orb(
                color: MultiverseBrandColors.nebulaViolet,
                peak: 0.4,
                center: CGPoint(x: size.width - 14 * drift, y: 330 + 18 * drift),
                radius: 160
            )
        )
        orbs.append(
            Orb(
                color: MultiverseBrandColors.portalGreen,
                peak: 0.42,
                center: CGPoint(x: size.width + 20 - 18 * drift, y: size.height - 74 - 14 * drift),
                radius: 300
            )
        )
        return orbs
    }

    /// A fixed starfield: the same seed on every launch, so the frame never changes between runs.
    private func drawStars(in context: inout GraphicsContext, size: CGSize) {
        var generator = SeededGenerator(seed: Self.starSeed)
        for _ in 0..<Self.starCount {
            let x = Double.random(in: 0...1, using: &generator) * size.width
            let y = Double.random(in: 0...1, using: &generator) * size.height
            let radius = Double.random(in: 0.5...1.4, using: &generator)
            let alpha = Double.random(in: 0.35...0.9, using: &generator)
            context.fill(
                Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)),
                with: .color(MultiverseLabelColors.primary.opacity(alpha))
            )
        }
    }

    private static let starSeed: UInt64 = 29_291
    private static let starCount = 60

    /// One aurora orb: a radial glow, strongest in its centre.
    private struct Orb {
        let color: Color
        let peak: Double
        let center: CGPoint
        let radius: CGFloat

        func draw(in context: inout GraphicsContext) {
            var stops: [Gradient.Stop] = []
            stops.append(.init(color: color.opacity(peak), location: 0))
            stops.append(.init(color: color.opacity(peak * 0.5), location: 0.35))
            stops.append(.init(color: color.opacity(peak * 0.3), location: 0.55))
            stops.append(.init(color: color.opacity(0), location: 1))
            let bounds = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
            context.fill(
                Path(ellipseIn: bounds),
                with: .radialGradient(Gradient(stops: stops), center: center, startRadius: 0, endRadius: radius)
            )
        }
    }
}

/// A small deterministic generator (SplitMix64), so the starfield is the same on every launch.
private struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var value = state
        value = (value ^ (value >> 30)) &* 0xBF58_476D_1CE4_E5B9
        value = (value ^ (value >> 27)) &* 0x94D0_49BB_1331_11EB
        return value ^ (value >> 31)
    }
}

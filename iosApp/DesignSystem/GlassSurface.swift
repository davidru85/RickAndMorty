import SwiftUI

// The one glass surface every iOS component shares (`UI_SPEC.md` §3.5, §4.2, §9;
// `GUIDELINES.md` §6.2).
//
// A component is **one** view with two visual paths, selected by an availability check — never two
// components and never a duplicated call site. This file is where that selection happens, once:
//
// - `GlassMaterial.glass` (iOS 26+) applies the system `glassEffect`; the spec's `Liquid Glass/*`
//   effects are approximated by the system material rather than hand-built (`UI_SPEC.md` §3.5).
// - `GlassMaterial.material` (iOS 18) is the documented pre-26 treatment: the system material plus
//   the `Glass/Fill` token and the 1 pt light-catching rim.
// - `GlassMaterial.opaqueMaterial` is the **Reduce Transparency** path (`UI_SPEC.md` §9): the
//   opaque `.thickMaterial` over `Surface Container`, with the rim kept and any tint still layered,
//   so a selected segment or a symbol well stays distinguishable.
//
// Both paths share the same parameters, the same shape and the same call-site surface, so a caller
// cannot tell which path rendered (`GUIDELINES.md` §6.2).

/// The shape a glass surface is drawn in, expressed without committing the call site to a concrete
/// shape type so one component can use a capsule or a continuous-corner rectangle behind the same
/// availability check.
public enum GlassShape: Equatable, Sendable {
    /// A continuous-curvature squircle of [radius] (`UI_SPEC.md` §3.3).
    case rounded(CGFloat)
    /// A capsule — the rendering of `Radius/Glass Control` (999) and of every glass control.
    case capsule

    /// The concrete shape the two paths draw in.
    public var shape: AnyShape {
        switch self {
        case .rounded(let radius):
            AnyShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        case .capsule:
            AnyShape(Capsule())
        }
    }
}

/// The visual path a glass surface renders (`GUIDELINES.md` §6.2). The OS version and the
/// accessibility setting together decide it; a component never selects one by hand.
public enum GlassMaterial: Equatable, Sendable {
    /// The Liquid Glass path (iOS 26+).
    case glass
    /// The pre-26 material fallback.
    case material
    /// The Reduce Transparency fallback: the opaque material (`UI_SPEC.md` §9).
    case opaqueMaterial

    /// The path for the current process and accessibility settings.
    ///
    /// This is the single selection point: Reduce Transparency wins on every OS, `#available(iOS 26)`
    /// chooses glass on a current one, and anything older gets the translucent material fallback, so
    /// the paths can never drift apart in their call sites.
    public static func resolved(reduceTransparency: Bool) -> GlassMaterial {
        if reduceTransparency {
            // `UI_SPEC.md` §9: with Reduce Transparency, glass is swapped for an opaque material,
            // so the glass path is not taken even where `glassEffect` exists.
            return .opaqueMaterial
        }
        if #available(iOS 26.0, *) {
            return .glass
        }
        return .material
    }

    /// Whether the path paints the opaque Reduce Transparency container.
    public var isOpaque: Bool { self == .opaqueMaterial }
}

/// The preview and snapshot seam for the paths (`DEC-025`, `TEST-UI-010`).
///
/// `TEST-UI-010` snapshots **both** paths so the fallback cannot drift, and each must be renderable
/// on one OS — the fallback is otherwise unreachable on an iOS 26 device. `nil`, the production
/// value, keeps the availability check and Reduce Transparency authoritative; a preview or a test
/// sets it to render the path it needs. It never changes layout or accessibility, only the paint.
private struct GlassPathOverrideKey: EnvironmentKey {
    static let defaultValue: GlassMaterial? = nil
}

extension EnvironmentValues {
    /// The forced glass path, or `nil` to resolve it from the OS and Reduce Transparency.
    public var multiverseGlassPath: GlassMaterial? {
        get { self[GlassPathOverrideKey.self] }
        set { self[GlassPathOverrideKey.self] = newValue }
    }
}

/// Draws a component's container in whichever glass path applies.
///
/// Not used directly by a component; `glassSurface(_:tint:shadow:)` is the call site.
private struct MultiverseGlassModifier: ViewModifier {
    let shape: GlassShape
    let tint: Color?
    let shadow: Color?
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.multiverseGlassPath) private var pathOverride

    private var path: GlassMaterial {
        pathOverride ?? GlassMaterial.resolved(reduceTransparency: reduceTransparency)
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        switch path {
        case .glass:
            glassPath(content)
        case .material, .opaqueMaterial:
            materialPath(content)
        }
    }

    /// The iOS 26+ Liquid Glass path. Guarded here and nowhere else.
    @ViewBuilder
    private func glassPath(_ content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.glassEffect(glass, in: shape.shape)
        } else {
            materialPath(content)
        }
    }

    @available(iOS 26.0, *)
    private var glass: Glass {
        var glass = Glass.regular
        if let tint {
            glass = glass.tint(tint)
        }
        return glass.interactive()
    }

    /// The pre-26 fallback and the Reduce Transparency path: the system material, the spec's rim and
    /// drop shadow.
    private func materialPath(_ content: Content) -> some View {
        content
            .background { materialFill }
            .overlay {
                shape.shape
                    .stroke(MultiverseGlassColors.rim, lineWidth: MultiverseDimensions.rimWidth)
            }
            .clipShape(shape.shape)
            .shadow(
                color: shadow ?? MultiverseGlassColors.shadow,
                radius: MultiverseDimensions.spaceL,
                y: MultiverseDimensions.spaceM
            )
    }

    /// The material path's fill.
    ///
    /// With Reduce Transparency (`UI_SPEC.md` §9) the container is **opaque** — `.thickMaterial`
    /// over `Surface Container`, the tone the Android side swaps to — with the tint still layered on
    /// top. Without it, the fill is the system material plus the `Glass/Fill` token, or the
    /// surface's own `Glass/Tint*` when one is given.
    @ViewBuilder
    private var materialFill: some View {
        // A `Material` is a `ShapeStyle`, not a `View`, so it fills a shape rather than sitting in a
        // stack directly. The opaque path adds `Surface Container` under the `.thickMaterial` so the
        // Reduce Transparency container is genuinely opaque (`UI_SPEC.md` §9); the translucent path
        // stacks only material plus the fill token, so the background still shows through.
        ZStack {
            if path.isOpaque {
                MultiverseColors.surfaceContainer
            }
            Rectangle().fill(baseFillStyle)
            Rectangle().fill(tint ?? MultiverseGlassColors.fill)
        }
    }

    /// `.thickMaterial` with Reduce Transparency, `.regularMaterial` otherwise (`UI_SPEC.md` §9).
    private var baseFillStyle: AnyShapeStyle {
        AnyShapeStyle(path.isOpaque ? Material.thickMaterial : Material.regularMaterial)
    }
}

extension View {
    /// Draws this view's container as a glass surface in whichever path the OS and accessibility
    /// settings select (`UI_SPEC.md` §3.5; `GUIDELINES.md` §6.2).
    ///
    /// - Parameters:
    ///   - shape: the container shape; `.capsule` for a glass control, `.rounded` for a card, bar or
    ///     panel (`UI_SPEC.md` §3.3).
    ///   - tint: the glass tint colour (`Glass/Tint Green`, `Glass/Tint Violet`) when the surface is
    ///     a selected segment or a symbol well.
    ///   - shadow: the drop-shadow colour; the default is `Glass/Shadow`.
    public func glassSurface(
        _ shape: GlassShape,
        tint: Color? = nil,
        shadow: Color? = nil
    ) -> some View {
        modifier(MultiverseGlassModifier(shape: shape, tint: tint, shadow: shadow))
    }
}

/// The emphasis of a glass button (`UI_SPEC.md` §4.2): a standard glass control, or the prominent
/// Portal Green one.
public enum GlassButtonEmphasis: Sendable {
    case standard
    case prominent
}

/// Applies the glass **button** treatment: the system `.glass`/`.glassProminent` style on iOS 26+,
/// the material surface on the fallback paths.
///
/// The selection lives here for the same reason the surface's does (`GUIDELINES.md` §6.2): a button
/// that selected its own path would duplicate the check, and the two paths of a button would then
/// be free to drift.
private struct GlassButtonModifier: ViewModifier {
    let emphasis: GlassButtonEmphasis
    let tint: Color
    let shape: GlassShape
    @Environment(\.multiverseGlassPath) private var pathOverride
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    private var path: GlassMaterial {
        pathOverride ?? GlassMaterial.resolved(reduceTransparency: reduceTransparency)
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        switch path {
        case .glass:
            glassButton(content)
        case .material, .opaqueMaterial:
            // The material paths draw their own container, so the button itself stays plain.
            content.buttonStyle(.plain).glassSurface(shape, tint: tint)
        }
    }

    @ViewBuilder
    private func glassButton(_ content: Content) -> some View {
        if #available(iOS 26.0, *) {
            switch emphasis {
            case .standard:
                content.buttonStyle(.glass)
            case .prominent:
                content.buttonStyle(.glassProminent).tint(tint)
            }
        } else {
            content.buttonStyle(.plain).glassSurface(shape, tint: tint)
        }
    }
}

extension View {
    /// Styles a `Button` as a glass control in whichever path the OS and accessibility settings
    /// select (`UI_SPEC.md` §4.2).
    ///
    /// - Parameters:
    ///   - emphasis: `.standard` or `.prominent`.
    ///   - tint: the prominent emphasis's tint; `Portal Green` for the spec's prominent buttons.
    ///   - shape: the button's container shape.
    public func glassButton(
        _ emphasis: GlassButtonEmphasis,
        tint: Color = MultiverseBrandColors.portalGreen,
        shape: GlassShape = .capsule
    ) -> some View {
        modifier(GlassButtonModifier(emphasis: emphasis, tint: tint, shape: shape))
    }
}

/// Groups sibling glass surfaces so they morph fluidly (`UI_SPEC.md` §4.2: "a custom control in a
/// `GlassEffectContainer`").
///
/// On iOS 26+ the content is a real `GlassEffectContainer`; on the fallback paths it is a plain
/// container, because the material path needs no grouping and the layout must not differ between
/// the paths (`GUIDELINES.md` §6.2).
public struct GlassContainer<Content: View>: View {
    private let spacing: CGFloat?
    private let content: () -> Content
    @Environment(\.multiverseGlassPath) private var pathOverride
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    public init(spacing: CGFloat? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.spacing = spacing
        self.content = content
    }

    public var body: some View {
        let path = pathOverride ?? GlassMaterial.resolved(reduceTransparency: reduceTransparency)
        Group {
            if path == .glass, #available(iOS 26.0, *) {
                GlassEffectContainer(spacing: spacing, content: content)
            } else {
                content()
            }
        }
    }
}

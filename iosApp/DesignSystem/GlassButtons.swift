import SwiftUI

// The Liquid Glass buttons of `UI_SPEC.md` §4.2 and §1.2.
//
// Every button is **one** view with two visual paths, selected only in `GlassSurface.swift`
// (`GUIDELINES.md` §6.2): `glassButton(_:tint:shape:)` applies the system `.glass`/`.glassProminent`
// style on iOS 26+ and the material surface on the fallbacks — the pre-26 material on iOS 18 and the
// opaque one under Reduce Transparency. A button never calls `glassEffect` or selects a path itself.
//
// The buttons take primitives only: a caller resolves the SF Symbol and the label (the label from the
// copy list), so the design system names no domain, presentation or route type (`DESIGN.md` §3.4).

/// The glass icon button's two styles (`UI_SPEC.md` §4.2): `Glass` (white symbol) and `Prominent`
/// (Portal Green tint, Space Black symbol).
public enum GlassIconButtonStyle: Sendable {
    case glass
    case prominent
}

/// The glass **icon button** (`UI_SPEC.md` §4.2, §1.2 `iOS/Glass icon button`): a 50 pt square touch
/// target in one of two styles — `Glass` (white symbol) or `Prominent` (Portal Green tint, Space
/// Black symbol). The two visual paths are chosen in `GlassSurface.swift`.
///
/// The spec's Favorite mapping is a caller decision — Glass + `heart` unmarked, Prominent +
/// `heart.fill` marked — so this component takes the symbol and the style and does not own the rule.
/// `accessibilityLabel` carries the control's label; a toggled state is the caller's
/// `.accessibilityAddTraits` at its call site.
public struct GlassIconButton: View {
    private let systemImage: String
    private let style: GlassIconButtonStyle
    private let accessibilityLabel: String
    private let action: () -> Void

    public init(
        systemImage: String,
        style: GlassIconButtonStyle = .glass,
        accessibilityLabel: String,
        action: @escaping () -> Void
    ) {
        self.systemImage = systemImage
        self.style = style
        self.accessibilityLabel = accessibilityLabel
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                // A symbol's font is a text style whose weight matches the control, per
                // `UI_SPEC.md` §3.4 ("hierarchical rendering, weight matched to adjacent text"), so
                // it scales with Dynamic Type and no raw size appears in the view.
                .font(MultiverseType.title3Semibold)
                .foregroundStyle(symbolColor)
                // A minimum, not a fixed, frame: the symbol scales with Dynamic Type, and at the
                // accessibility sizes a fixed 50 pt frame let it spill over its own glass shape
                // (`REQ-UX-006`). At the default sizes the frame is the specified 50 pt.
                .padding(MultiverseDimensions.spaceXs)
                .frame(
                    minWidth: MultiverseDimensions.glassIconButton,
                    minHeight: MultiverseDimensions.glassIconButton
                )
                .contentShape(Capsule())
        }
        .glassButton(emphasis)
        .accessibilityLabel(Text(accessibilityLabel))
    }

    /// The symbol colour of the two styles: white for `Glass`, Space Black on the Portal Green tint
    /// for `Prominent`.
    private var symbolColor: Color {
        style == .prominent ? MultiverseBrandColors.spaceBlack : MultiverseLabelColors.primary
    }

    private var emphasis: GlassButtonEmphasis {
        style == .prominent ? .prominent : .standard
    }
}

/// The glass **text button** (`UI_SPEC.md` §1.2 `iOS/Glass text button`, §6.4): a capsule glass
/// prominent button in Portal Green, with the same two paths as every glass surface.
///
/// It is the empty state's only action; the caller resolves its label from the copy list.
public struct GlassTextButton: View {
    private let label: String
    private let action: () -> Void

    public init(label: String, action: @escaping () -> Void) {
        self.label = label
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(label)
                .font(MultiverseType.headline)
                .foregroundStyle(MultiverseBrandColors.spaceBlack)
                .padding(.horizontal, MultiverseDimensions.spaceXl)
                .padding(.vertical, MultiverseDimensions.spaceM)
                .contentShape(Capsule())
        }
        .glassButton(.prominent, tint: MultiverseBrandColors.portalGreen)
    }
}

#Preview("Glass icon buttons") {
    HStack(spacing: MultiverseDimensions.spaceL) {
        GlassIconButton(systemImage: "heart", style: .glass, accessibilityLabel: "Favorite") {}
        GlassIconButton(systemImage: "heart.fill", style: .prominent, accessibilityLabel: "Favorite") {}
    }
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .preferredColorScheme(.dark)
}

#Preview("Glass text button — default and fallback") {
    VStack(spacing: MultiverseDimensions.spaceL) {
        GlassTextButton(label: "Browse characters") {}
        GlassTextButton(label: "Browse characters") {}
            .environment(\.multiverseGlassPath, .material)
    }
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .preferredColorScheme(.dark)
}

#Preview("Glass buttons — Reduce Transparency") {
    GlassTextButton(label: "Browse characters") {}
        .padding()
        .background(MultiverseBrandColors.spaceBlack)
        .transformEnvironment(\.multiverseGlassPath) { $0 = .opaqueMaterial }
        .preferredColorScheme(.dark)
}

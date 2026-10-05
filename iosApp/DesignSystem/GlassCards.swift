import SwiftUI

// The Liquid Glass card components of `UI_SPEC.md` §4.2 and §1.2.
//
// Every component here is **one** view with two visual paths, and the selection happens only in
// `GlassSurface.swift` (`GUIDELINES.md` §6.2): `glassSurface(_:tint:shadow:)` draws the iOS 26+
// `glassEffect` path on a current OS and the material path on the fallbacks — the pre-26 material on
// iOS 18 and the opaque one under Reduce Transparency. A component never calls `glassEffect`
// itself, so no call site duplicates the check and the paths cannot drift apart.
//
// Components take primitives only — no domain, presentation or route type — so the design system
// stays a leaf and a caller resolves its own values at the feature or shell boundary
// (`DESIGN.md` §3.4). User-visible text is passed in, never literal (`GUIDELINES.md` §5.7).

/// The glass **character card** (`UI_SPEC.md` §4.2, §1.2 `iOS/Glass character card`): the portrait
/// with a bottom-anchored glass bar holding the name (Headline, up to 2 lines) and the status row
/// (a 7 pt dot plus "Status · Species", Caption 1).
///
/// The card is 177 × 236 pt with a continuous 26 corner, and the bar is full width minus a 6 pt
/// inset with a 20 corner. The two visual paths are chosen in `GlassSurface.swift`: the iOS 26+
/// `glassEffect` on a current OS, the material fallback on iOS 18 or under Reduce Transparency.
///
/// The portrait is a caller-supplied `Image`, so the card carries no image loader and no domain
/// type. It is overscanned by 14 pt and moves at 0.85× the scroll of whatever scroll view holds the
/// card ([CardParallax]); outside a scroll view, or with Reduce Motion, it stays centred.
public struct GlassCharacterCard: View {
    private let name: String
    private let species: String
    private let statusTone: StatusTone
    private let statusLabel: String
    private let portrait: Image

    public init(
        name: String,
        species: String,
        statusTone: StatusTone,
        statusLabel: String,
        portrait: Image
    ) {
        self.name = name
        self.species = species
        self.statusTone = statusTone
        self.statusLabel = statusLabel
        self.portrait = portrait
    }

    /// The reader's text size (`UI_SPEC.md` §9): at the accessibility sizes the grid is one column.
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    /// Reduce Motion disables the portrait's parallax (`UI_SPEC.md` §7).
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public var body: some View {
        shaped
            .clipShape(
                RoundedRectangle(cornerRadius: MultiverseDimensions.glassCard, style: .continuous)
            )
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("\(name), \(statusLabel), \(species)"))
    }

    /// The specified 177 × 236 pt card at the default sizes. At the accessibility sizes the grid has
    /// one column, so the card takes that column's width and grows to fit its text instead of
    /// truncating it (`REQ-UX-006`, `AC-REQ-UX-006-1`); the portrait fills whatever size results.
    @ViewBuilder
    private var shaped: some View {
        if dynamicTypeSize.isAccessibilitySize {
            bar
                .padding(MultiverseDimensions.glassCardInset)
                .frame(maxWidth: .infinity, minHeight: MultiverseDimensions.glassCardHeight, alignment: .bottom)
                .background {
                    portrait
                        .resizable()
                        .scaledToFill()
                }
                .clipped()
        } else {
            // The portrait is taller than the card by the overscan at each end, and the parallax moves it
            // within that margin, so no edge of the image ever shows.
            portrait
                .resizable()
                .scaledToFill()
                .frame(
                    width: MultiverseDimensions.glassCardWidth,
                    height: MultiverseDimensions.glassCardHeight + 2 * CardParallax.overscan
                )
                .visualEffect { [reduceMotion] content, proxy in
                    content.offset(y: CardParallax.offset(in: proxy, reduceMotion: reduceMotion))
                }
                .frame(
                    width: MultiverseDimensions.glassCardWidth,
                    height: MultiverseDimensions.glassCardHeight
                )
                .clipped()
                .overlay(alignment: .bottom) {
                    bar.padding(MultiverseDimensions.glassCardInset)
                }
        }
    }

    /// The glass bar: the name and the status row, over the card's own glass surface.
    private var bar: some View {
        VStack(alignment: .leading, spacing: MultiverseDimensions.spaceXs) {
            Text(name)
                .font(MultiverseType.headline)
                .foregroundStyle(MultiverseLabelColors.primary)
                // No cap at the accessibility sizes: the card grows rather than truncating the name.
                .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
            HStack(spacing: MultiverseDimensions.spaceS) {
                Circle()
                    .fill(statusTone.dotColor)
                    .frame(
                        width: MultiverseDimensions.statusDot,
                        height: MultiverseDimensions.statusDot
                    )
                Text("\(statusLabel) · \(species)")
                    .font(MultiverseType.caption1)
                    .foregroundStyle(MultiverseLabelColors.secondary)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(MultiverseDimensions.spaceM)
        .glassSurface(.rounded(MultiverseDimensions.glassCardBarCorner))
    }
}

/// The glass card portrait's scroll parallax (`UI_SPEC.md` §4.2, §7): the portrait moves at 0.85× the
/// scroll, so inside its card it lags by the remaining 0.15× of the card's distance from the scroll
/// view's centre, within the 14 pt overscan, and is centred when the card is.
public enum CardParallax {
    /// The portrait's overscan at each end (`UI_SPEC.md` §4.2).
    public static let overscan: CGFloat = MultiverseDimensions.glassCardPortraitOverscan

    /// The portrait's speed relative to the scroll (`UI_SPEC.md` §4.2).
    public static let speed: CGFloat = 0.85

    /// The portrait's vertical offset inside its card for a card [distanceFromCenter] points below the
    /// scroll view's centre (negative when above it); none with Reduce Motion.
    public static func offset(distanceFromCenter: CGFloat, reduceMotion: Bool) -> CGFloat {
        guard !reduceMotion else { return 0 }
        return min(max(-distanceFromCenter * (1 - speed), -overscan), overscan)
    }

    /// The offset for the view [proxy] measures, from its place in the enclosing scroll view; none
    /// outside a scroll view.
    public static func offset(in proxy: GeometryProxy, reduceMotion: Bool) -> CGFloat {
        guard let scroll = proxy.bounds(of: .scrollView) else { return 0 }
        return offset(distanceFromCenter: proxy.size.height / 2 - scroll.midY, reduceMotion: reduceMotion)
    }
}

/// The glass **info row** (`UI_SPEC.md` §4.2, §1.2 `iOS/Glass info row`): a 38 pt symbol well in
/// `Glass/Tint Green` with the symbol in Portal Glow, a Footnote secondary label and a Headline
/// value, in a `LabeledContent`-shaped row for the frosted panel.
public struct GlassInfoRow: View {
    private let symbol: String
    private let label: String
    private let value: String

    public init(symbol: String, label: String, value: String) {
        self.symbol = symbol
        self.label = label
        self.value = value
    }

    public var body: some View {
        HStack(spacing: MultiverseDimensions.spaceM) {
            symbolWell
            LabeledContent {
                Text(value)
                    .font(MultiverseType.headline)
                    .foregroundStyle(MultiverseLabelColors.primary)
            } label: {
                Text(label)
                    .font(MultiverseType.footnote)
                    .foregroundStyle(MultiverseLabelColors.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// The symbol well: `Glass/Tint Green` on the component's own glass surface, the symbol in
    /// Portal Glow (`UI_SPEC.md` §4.2).
    private var symbolWell: some View {
        Image(systemName: symbol)
            // A matched text style rather than a raw size, so the symbol scales with Dynamic Type
            // (`GUIDELINES.md` §6.1, `UI_SPEC.md` §3.4).
            .font(MultiverseType.headline)
            .foregroundStyle(MultiverseBrandColors.portalGlow)
            .frame(
                width: MultiverseDimensions.infoRowSymbolWell,
                height: MultiverseDimensions.infoRowSymbolWell
            )
            .glassSurface(
                .rounded(MultiverseDimensions.cornerMedium),
                tint: MultiverseGlassColors.tintGreen
            )
    }
}

/// The glass **status capsule** (`UI_SPEC.md` §4.2): a dot and the status label in Footnote
/// Emphasized on a clear glass capsule. Status is never colour-only, so the label always accompanies
/// the dot (`REQ-UX-005`).
public struct GlassStatusCapsule: View {
    private let tone: StatusTone
    private let label: String

    public init(tone: StatusTone, label: String) {
        self.tone = tone
        self.label = label
    }

    public var body: some View {
        HStack(spacing: MultiverseDimensions.spaceS) {
            Circle()
                .fill(tone.dotColor)
                .frame(width: MultiverseDimensions.statusDot, height: MultiverseDimensions.statusDot)
            Text(label)
                .font(MultiverseType.footnoteEmphasized)
                .foregroundStyle(MultiverseLabelColors.primary)
        }
        .padding(.horizontal, MultiverseDimensions.spaceM)
        .padding(.vertical, MultiverseDimensions.spaceXs)
        .glassSurface(.capsule, tint: MultiverseGlassColors.fillStrong)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label))
    }
}

#Preview("Glass card and status capsule") {
    VStack(spacing: MultiverseDimensions.spaceL) {
        GlassCharacterCard(
            name: "Rick Sanchez",
            species: "Human",
            statusTone: .alive,
            statusLabel: "Alive",
            portrait: Image(systemName: "photo")
        )
        GlassStatusCapsule(tone: .alive, label: "Alive")
    }
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .preferredColorScheme(.dark)
}

#Preview("Glass card — material fallback") {
    GlassCharacterCard(
        name: "Rick Sanchez",
        species: "Human",
        statusTone: .alive,
        statusLabel: "Alive",
        portrait: Image(systemName: "photo")
    )
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .environment(\.multiverseGlassPath, .material)
    .preferredColorScheme(.dark)
}

#Preview("Glass card — Reduce Transparency") {
    GlassCharacterCard(
        name: "Rick Sanchez",
        species: "Human",
        statusTone: .alive,
        statusLabel: "Alive",
        portrait: Image(systemName: "photo")
    )
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .transformEnvironment(\.multiverseGlassPath) { $0 = .opaqueMaterial }
    .preferredColorScheme(.dark)
}

#Preview("Glass info row — default and largest Dynamic Type") {
    VStack(spacing: MultiverseDimensions.spaceL) {
        GlassInfoRow(symbol: "globe", label: "Origin", value: "Earth (C-137)")
        GlassInfoRow(symbol: "globe", label: "Origin", value: "Earth (C-137)")
            .dynamicTypeSize(.accessibility5)
    }
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .preferredColorScheme(.dark)
}

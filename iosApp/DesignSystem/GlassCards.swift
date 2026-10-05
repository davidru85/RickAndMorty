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
/// type; the spec's parallax (`.scrollTransition` at 0.85× over the 14 pt overscan) belongs to the
/// scrolling screen that lays the grid out, not to the card.
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
            portrait
                .resizable()
                .scaledToFill()
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

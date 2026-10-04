import SwiftUI

// The design system's token objects (`DEC-022`, `DEC-102`), the Swift peer of the Kotlin
// `MultiverseTokens.kt`. Every value mirrors `docs/figma/tokens.json`, the committed export that
// `TEST-UNIT-035` compares in both directions (`MultiverseTokensParityTests`): a Swift value that
// drifts fails, and an exported variable no token maps fails too. Figma wins for values
// (`UI_SPEC.md`), so a divergence is settled by re-exporting, never by editing a literal here alone.
//
// The names mirror the Kotlin objects one-for-one (`GUIDELINES.md` §7.3), so a reader compares
// `MultiverseColors.primary` here with `MultiverseColors.primary` there.
//
// No view carries an ad-hoc literal for a value this file names (`GUIDELINES.md` §6.1,
// `UI_SPEC.md` §3): a raw `Color(red:)` or a hand-picked corner radius is a review finding.

extension Color {
    /// Builds a token colour from its export form (`#RRGGBB` or `#RRGGBBAA`), the same string the
    /// Figma export carries, so the token and the parity test read one representation.
    public init(multiverseHex hex: String) {
        var digits = hex
        if digits.hasPrefix("#") {
            digits.removeFirst()
        }
        guard digits.count == 6 || digits.count == 8, let raw = UInt64(digits, radix: 16) else {
            preconditionFailure("`\(hex)` is not an export-form colour (#RRGGBB or #RRGGBBAA)")
        }
        let shift = digits.count == 8 ? 24 : 16
        self.init(
            .sRGB,
            red: Double((raw >> UInt64(shift)) & 0xFF) / 255,
            green: Double((raw >> UInt64(shift - 8)) & 0xFF) / 255,
            blue: Double((raw >> UInt64(shift - 16)) & 0xFF) / 255,
            opacity: digits.count == 8 ? Double(raw & 0xFF) / 255 : 1
        )
    }
}

/// The single `Multiverse · M3 Scheme` appearance: the 49 roles `UI_SPEC.md` §3.1 names and
/// `tokens.json` exports.
public enum MultiverseColors {
    public static let primary = Color(multiverseHex: "#A4D661")
    public static let onPrimary = Color(multiverseHex: "#2C4800")
    public static let primaryContainer = Color(multiverseHex: "#497401")
    public static let onPrimaryContainer = Color(multiverseHex: "#FFFFFF")
    public static let secondary = Color(multiverseHex: "#B5CDB4")
    public static let onSecondary = Color(multiverseHex: "#314532")
    public static let secondaryContainer = Color(multiverseHex: "#2C402E")
    public static let onSecondaryContainer = Color(multiverseHex: "#AEC5AD")
    public static let tertiary = Color(multiverseHex: "#A68CFF")
    public static let onTertiary = Color(multiverseHex: "#24006B")
    public static let tertiaryContainer = Color(multiverseHex: "#997BFE")
    public static let onTertiaryContainer = Color(multiverseHex: "#12003E")
    public static let error = Color(multiverseHex: "#F97758")
    public static let onError = Color(multiverseHex: "#450900")
    public static let errorContainer = Color(multiverseHex: "#85230A")
    public static let onErrorContainer = Color(multiverseHex: "#FF9B82")

    // The `* Fixed` / `* Fixed Dim` roles are part of the same single scheme. No iOS surface renders
    // them yet, but they are roles of the one appearance, so they are mapped rather than excluded
    // (`TEST-UNIT-035`'s exclusion list is reserved for variables a platform genuinely omits).
    public static let primaryFixed = Color(multiverseHex: "#BFF27A")
    public static let primaryFixedDim = Color(multiverseHex: "#B1E46E")
    public static let onPrimaryFixed = Color(multiverseHex: "#2B4700")
    public static let onPrimaryFixedVariant = Color(multiverseHex: "#406600")
    public static let secondaryFixed = Color(multiverseHex: "#D1E9CF")
    public static let secondaryFixedDim = Color(multiverseHex: "#C3DBC1")
    public static let onSecondaryFixed = Color(multiverseHex: "#304431")
    public static let onSecondaryFixedVariant = Color(multiverseHex: "#4C614C")
    public static let tertiaryFixed = Color(multiverseHex: "#997BFE")
    public static let tertiaryFixedDim = Color(multiverseHex: "#8C6EEF")
    public static let onTertiaryFixed = Color(multiverseHex: "#000000")
    public static let onTertiaryFixedVariant = Color(multiverseHex: "#200060")

    public static let surface = Color(multiverseHex: "#0F0E12")
    public static let onSurface = Color(multiverseHex: "#E9E3EF")
    public static let surfaceVariant = Color(multiverseHex: "#27242D")
    public static let onSurfaceVariant = Color(multiverseHex: "#AEA9B4")
    public static let surfaceContainerLowest = Color(multiverseHex: "#000000")
    public static let surfaceContainerLow = Color(multiverseHex: "#141318")
    public static let surfaceContainer = Color(multiverseHex: "#1A191F")
    public static let surfaceContainerHigh = Color(multiverseHex: "#211E26")
    public static let surfaceContainerHighest = Color(multiverseHex: "#27242D")
    public static let surfaceDim = Color(multiverseHex: "#0F0E12")
    public static let surfaceBright = Color(multiverseHex: "#2D2B34")
    public static let inverseSurface = Color(multiverseHex: "#FDF8FE")
    public static let inverseOnSurface = Color(multiverseHex: "#575459")
    public static let inversePrimary = Color(multiverseHex: "#426A00")
    public static let outline = Color(multiverseHex: "#78747E")
    public static let outlineVariant = Color(multiverseHex: "#494650")
    public static let background = Color(multiverseHex: "#0F0E12")
    public static let onBackground = Color(multiverseHex: "#E9E3EF")
    public static let shadow = Color(multiverseHex: "#000000")
    public static let scrim = Color(multiverseHex: "#000000")
    public static let surfaceTint = Color(multiverseHex: "#A4D661")
}

/// The brand and status colours (`UI_SPEC.md` §3.2).
public enum MultiverseBrandColors {
    public static let portalGreen = Color(multiverseHex: "#97CE4C")
    public static let portalGlow = Color(multiverseHex: "#C6FF6B")
    public static let cosmicViolet = Color(multiverseHex: "#7B4DFF")
    public static let nebulaViolet = Color(multiverseHex: "#B69CFF")
    public static let spaceBlack = Color(multiverseHex: "#07060B")

    public static let statusAlive = Color(multiverseHex: "#7EE06A")
    public static let statusDead = Color(multiverseHex: "#FF6B6B")
    public static let statusUnknown = Color(multiverseHex: "#B8B4BF")
}

/// The Liquid Glass fills, strokes and tints (`UI_SPEC.md` §3.2, §4.2). These are the iOS-only
/// variables the Kotlin side lists as reviewed exclusions; iOS consumes them.
public enum MultiverseGlassColors {
    /// Base fill under glass effects.
    public static let fill = Color(multiverseHex: "#FFFFFF14")
    /// Selected capsules and the status capsule.
    public static let fillStrong = Color(multiverseHex: "#FFFFFF29")
    /// Top edge of the light-catching rim.
    public static let strokeHighlight = Color(multiverseHex: "#FFFFFF6B")
    /// Middle of the rim.
    public static let strokeEdge = Color(multiverseHex: "#FFFFFF1A")
    /// Selected segment and symbol wells.
    public static let tintGreen = Color(multiverseHex: "#97CE4C4D")
    /// Reserved.
    public static let tintViolet = Color(multiverseHex: "#7B4DFF66")
    /// Glass drop shadow.
    public static let shadow = Color(multiverseHex: "#00000059")

    /// The 1 pt light-catching rim (`UI_SPEC.md` §3.2): a vertical gradient white 55 % → 6 % → 25 %.
    ///
    /// Derived from the rim's three stops rather than from a variable, so it is not a parity entry;
    /// the stops are the spec's, not a hand-picked ramp (`GUIDELINES.md` §6.1).
    public static let rim = LinearGradient(
        stops: rimStops,
        startPoint: .top,
        endPoint: .bottom
    )

    /// The rim's stops, built by appending rather than as a multiline collection literal: the
    /// repository's formatter and linter disagree about trailing commas in one (see the note in the
    /// parity test), so a multiline literal cannot pass both tools.
    private static var rimStops: [Gradient.Stop] {
        var stops: [Gradient.Stop] = []
        stops.append(.init(color: Color.white.opacity(0.55), location: 0))
        stops.append(.init(color: Color.white.opacity(0.06), location: 0.5))
        stops.append(.init(color: Color.white.opacity(0.25), location: 1))
        return stops
    }
}

/// The iOS label family (`UI_SPEC.md` §3.2).
public enum MultiverseLabelColors {
    public static let primary = Color(multiverseHex: "#FFFFFF")
    public static let secondary = Color(multiverseHex: "#EBEBF5AD")
    public static let tertiary = Color(multiverseHex: "#EBEBF55C")
}

/// Spacing, shape and the glass radii (`UI_SPEC.md` §3.3).
public enum MultiverseDimensions {
    public static let spaceXs: CGFloat = 4
    public static let spaceS: CGFloat = 8
    public static let spaceM: CGFloat = 12
    public static let spaceL: CGFloat = 16
    public static let spaceXl: CGFloat = 24
    public static let space2Xl: CGFloat = 32
    public static let space3Xl: CGFloat = 48

    public static let cornerExtraSmall: CGFloat = 4
    public static let cornerSmall: CGFloat = 8
    public static let cornerMedium: CGFloat = 12
    public static let cornerLarge: CGFloat = 16
    public static let cornerLargeIncreased: CGFloat = 20
    public static let cornerExtraLarge: CGFloat = 28
    public static let cornerExtraLargeIncreased: CGFloat = 32
    public static let cornerExtraExtraLarge: CGFloat = 48
    /// `Shape/Corner Full` — the Figma value 999, rendered as a fully rounded corner.
    public static let cornerFull: CGFloat = 999

    /// `Radius/Glass Control` — the capsule radius of a glass control.
    public static let glassControl: CGFloat = 999
    /// `Radius/Glass Card` — the glass bar inside a character card.
    public static let glassCard: CGFloat = 26
    /// `Radius/Glass Panel` — the detail information panel.
    public static let glassPanel: CGFloat = 34

    // The fixed geometry of the `UI_SPEC.md` §4.2/§6.4 components. Every other value is a
    // dimension token above; these are the component dimensions the specification fixes, named here
    // so no view carries a hand-picked number (`GUIDELINES.md` §6.1).
    /// The iOS grid gutter (`UI_SPEC.md` §3.3): 16.
    public static let gridGutter: CGFloat = 16
    /// A glass control's track width (`UI_SPEC.md` §4.2): 370.
    public static let glassContainerWidth: CGFloat = 370
    /// The glass segmented control's height (`UI_SPEC.md` §4.2): 44.
    public static let glassControlHeight: CGFloat = 44
    /// The glass segmented control's inner inset (`UI_SPEC.md` §4.2): 4.
    public static let glassControlInset: CGFloat = 4
    /// The glass search field's height (`UI_SPEC.md` §4.2): 48.
    public static let glassSearchFieldHeight: CGFloat = 48
    /// The glass icon button's touch target (`UI_SPEC.md` §4.2, §9): 50.
    public static let glassIconButton: CGFloat = 50
    /// The glass character card's width and height (`UI_SPEC.md` §4.2): 177 × 236.
    public static let glassCardWidth: CGFloat = 177
    public static let glassCardHeight: CGFloat = 236
    /// The inset of the card's glass bar and its portrait (`UI_SPEC.md` §4.2): 6.
    public static let glassCardInset: CGFloat = 6
    /// The card's glass bar corner (`UI_SPEC.md` §4.2): 20.
    public static let glassCardBarCorner: CGFloat = 20
    /// The portrait's scroll overscan (`UI_SPEC.md` §4.2): 14.
    public static let glassCardPortraitOverscan: CGFloat = 14
    /// The status row's dot (`UI_SPEC.md` §4.2): 7.
    public static let statusDot: CGFloat = 7
    /// The glass info row's symbol well (`UI_SPEC.md` §4.2): 38.
    public static let infoRowSymbolWell: CGFloat = 38
    /// The empty state's glass symbol well (`UI_SPEC.md` §6.4): 120.
    public static let emptyStateWell: CGFloat = 120
    /// The empty state's centred text column (`UI_SPEC.md` §6.4): 320.
    public static let emptyStateTextWidth: CGFloat = 320
    /// The frosted panel's inner padding (`UI_SPEC.md` §4.2): 20.
    public static let glassPanelPadding: CGFloat = 20
    /// A settings panel's corner (`UI_SPEC.md` §4.2, "Settings section": `Liquid Glass – Regular –
    /// Small`, continuous corner 26 — the kit component's small corner, not the 34 of the larger
    /// frosted panel above).
    public static let settingsPanelCorner: CGFloat = 26
    /// A settings row's height (`UI_SPEC.md` §4.2, "Settings row": the iOS kit `Row`, Tall, 68 pt).
    public static let settingsRowHeight: CGFloat = 68
    /// The 1 pt light-catching rim (`UI_SPEC.md` §3.2).
    public static let rimWidth: CGFloat = 1
}

/// The iOS type scale (`UI_SPEC.md` §3.4), SwiftUI text styles so every style scales with Dynamic
/// Type (`REQ-UX-006`). The editorial display is the one fixed size; it scales through
/// `@ScaledMetric` at its call site, as the spec prescribes.
public enum MultiverseType {
    public static let editorialDisplaySize: CGFloat = 40
    public static let editorialDisplayWeight: Font.Weight = .heavy

    public static let largeTitleBold: Font = Font.largeTitle.bold()
    public static let title2Bold: Font = Font.title2.bold()
    public static let title3Semibold: Font = Font.title3.weight(.semibold)
    public static let headline: Font = Font.headline
    public static let body: Font = Font.body
    public static let subheadline: Font = Font.subheadline
    public static let subheadlineEmphasized: Font = Font.subheadline.weight(.semibold)
    public static let footnote: Font = Font.footnote
    public static let footnoteEmphasized: Font = Font.footnote.weight(.semibold)
    public static let caption1: Font = Font.caption.weight(.medium)
    public static let caption2Emphasized: Font = Font.caption2.weight(.semibold)

    /// The editorial display (`UI_SPEC.md` §3.4): SF Pro Expanded Heavy at 40 pt — the one fixed
    /// size in the scale, which a view scales through `@ScaledMetric` at its call site.
    public static let editorialDisplay: Font = Font.system(
        size: editorialDisplaySize,
        weight: editorialDisplayWeight,
        design: .default
    ).width(.expanded)

    /// The empty state's illustrated symbol (`UI_SPEC.md` §6.4): 48 pt. It follows font scale like
    /// the rest of the type scale (`REQ-UX-006`), so it belongs with the type tokens rather than the
    /// fixed component geometry.
    public static let emptyStateSymbolSize: CGFloat = 48
}

/// The status mirror a badge or a card renders (`UI_SPEC.md` §4.1): the dot's colour is data, the
/// label is copy the caller already resolved. The design system names no domain type, so a caller
/// maps its own status to this mirror at the feature or shell boundary (`DESIGN.md` §3.4).
public enum StatusTone {
    case alive
    case dead
    case unknown

    /// The dot colour of `UI_SPEC.md` §3.2's status family.
    public var dotColor: Color {
        switch self {
        case .alive: MultiverseBrandColors.statusAlive
        case .dead: MultiverseBrandColors.statusDead
        case .unknown: MultiverseBrandColors.statusUnknown
        }
    }
}

/// The export variable names the token objects above map, with each token's typed value.
///
/// `MultiverseTokensParityTests` (`TEST-UNIT-035`) reads this map: it is the one place the Swift
/// values and the committed export are compared, so a new token must be added here in the same
/// change. The map is typed rather than pre-formatted so the test derives the export form from the
/// `Color`/`CGFloat` itself, exactly as the Kotlin parity test derives it from the Compose `Color`.
public enum MultiverseTokens {
    /// Every mapped colour, keyed by its export variable name.
    public static var colors: [String: Color] {
        var colors: [String: Color] = [:]
        colors["Schemes/Primary"] = MultiverseColors.primary
        colors["Schemes/On Primary"] = MultiverseColors.onPrimary
        colors["Schemes/Primary Container"] = MultiverseColors.primaryContainer
        colors["Schemes/On Primary Container"] = MultiverseColors.onPrimaryContainer
        colors["Schemes/Secondary"] = MultiverseColors.secondary
        colors["Schemes/On Secondary"] = MultiverseColors.onSecondary
        colors["Schemes/Secondary Container"] = MultiverseColors.secondaryContainer
        colors["Schemes/On Secondary Container"] = MultiverseColors.onSecondaryContainer
        colors["Schemes/Tertiary"] = MultiverseColors.tertiary
        colors["Schemes/On Tertiary"] = MultiverseColors.onTertiary
        colors["Schemes/Tertiary Container"] = MultiverseColors.tertiaryContainer
        colors["Schemes/On Tertiary Container"] = MultiverseColors.onTertiaryContainer
        colors["Schemes/Error"] = MultiverseColors.error
        colors["Schemes/On Error"] = MultiverseColors.onError
        colors["Schemes/Error Container"] = MultiverseColors.errorContainer
        colors["Schemes/On Error Container"] = MultiverseColors.onErrorContainer
        colors["Schemes/Primary Fixed"] = MultiverseColors.primaryFixed
        colors["Schemes/Primary Fixed Dim"] = MultiverseColors.primaryFixedDim
        colors["Schemes/On Primary Fixed"] = MultiverseColors.onPrimaryFixed
        colors["Schemes/On Primary Fixed Variant"] = MultiverseColors.onPrimaryFixedVariant
        colors["Schemes/Secondary Fixed"] = MultiverseColors.secondaryFixed
        colors["Schemes/Secondary Fixed Dim"] = MultiverseColors.secondaryFixedDim
        colors["Schemes/On Secondary Fixed"] = MultiverseColors.onSecondaryFixed
        colors["Schemes/On Secondary Fixed Variant"] = MultiverseColors.onSecondaryFixedVariant
        colors["Schemes/Tertiary Fixed"] = MultiverseColors.tertiaryFixed
        colors["Schemes/Tertiary Fixed Dim"] = MultiverseColors.tertiaryFixedDim
        colors["Schemes/On Tertiary Fixed"] = MultiverseColors.onTertiaryFixed
        colors["Schemes/On Tertiary Fixed Variant"] = MultiverseColors.onTertiaryFixedVariant
        colors["Schemes/Surface"] = MultiverseColors.surface
        colors["Schemes/On Surface"] = MultiverseColors.onSurface
        colors["Schemes/Surface Variant"] = MultiverseColors.surfaceVariant
        colors["Schemes/On Surface Variant"] = MultiverseColors.onSurfaceVariant
        colors["Schemes/Surface Container Lowest"] = MultiverseColors.surfaceContainerLowest
        colors["Schemes/Surface Container Low"] = MultiverseColors.surfaceContainerLow
        colors["Schemes/Surface Container"] = MultiverseColors.surfaceContainer
        colors["Schemes/Surface Container High"] = MultiverseColors.surfaceContainerHigh
        colors["Schemes/Surface Container Highest"] = MultiverseColors.surfaceContainerHighest
        colors["Schemes/Surface Dim"] = MultiverseColors.surfaceDim
        colors["Schemes/Surface Bright"] = MultiverseColors.surfaceBright
        colors["Schemes/Inverse Surface"] = MultiverseColors.inverseSurface
        colors["Schemes/Inverse On Surface"] = MultiverseColors.inverseOnSurface
        colors["Schemes/Inverse Primary"] = MultiverseColors.inversePrimary
        colors["Schemes/Outline"] = MultiverseColors.outline
        colors["Schemes/Outline Variant"] = MultiverseColors.outlineVariant
        colors["Schemes/Background"] = MultiverseColors.background
        colors["Schemes/On Background"] = MultiverseColors.onBackground
        colors["Schemes/Shadow"] = MultiverseColors.shadow
        colors["Schemes/Scrim"] = MultiverseColors.scrim
        colors["Schemes/Surface Tint"] = MultiverseColors.surfaceTint
        colors["Brand/Portal Green"] = MultiverseBrandColors.portalGreen
        colors["Brand/Portal Glow"] = MultiverseBrandColors.portalGlow
        colors["Brand/Cosmic Violet"] = MultiverseBrandColors.cosmicViolet
        colors["Brand/Nebula Violet"] = MultiverseBrandColors.nebulaViolet
        colors["Brand/Space Black"] = MultiverseBrandColors.spaceBlack
        colors["Status/Alive"] = MultiverseBrandColors.statusAlive
        colors["Status/Dead"] = MultiverseBrandColors.statusDead
        colors["Status/Unknown"] = MultiverseBrandColors.statusUnknown
        colors["Glass/Fill"] = MultiverseGlassColors.fill
        colors["Glass/Fill Strong"] = MultiverseGlassColors.fillStrong
        colors["Glass/Stroke Highlight"] = MultiverseGlassColors.strokeHighlight
        colors["Glass/Stroke Edge"] = MultiverseGlassColors.strokeEdge
        colors["Glass/Tint Green"] = MultiverseGlassColors.tintGreen
        colors["Glass/Tint Violet"] = MultiverseGlassColors.tintViolet
        colors["Glass/Shadow"] = MultiverseGlassColors.shadow
        colors["Label/Primary"] = MultiverseLabelColors.primary
        colors["Label/Secondary"] = MultiverseLabelColors.secondary
        colors["Label/Tertiary"] = MultiverseLabelColors.tertiary
        return colors
    }

    /// Every mapped dimension, keyed by its export variable name.
    public static var dimensions: [String: CGFloat] {
        var dimensions: [String: CGFloat] = [:]
        dimensions["Space/XS"] = MultiverseDimensions.spaceXs
        dimensions["Space/S"] = MultiverseDimensions.spaceS
        dimensions["Space/M"] = MultiverseDimensions.spaceM
        dimensions["Space/L"] = MultiverseDimensions.spaceL
        dimensions["Space/XL"] = MultiverseDimensions.spaceXl
        dimensions["Space/2XL"] = MultiverseDimensions.space2Xl
        dimensions["Space/3XL"] = MultiverseDimensions.space3Xl
        dimensions["Shape/Corner Extra Small"] = MultiverseDimensions.cornerExtraSmall
        dimensions["Shape/Corner Small"] = MultiverseDimensions.cornerSmall
        dimensions["Shape/Corner Medium"] = MultiverseDimensions.cornerMedium
        dimensions["Shape/Corner Large"] = MultiverseDimensions.cornerLarge
        dimensions["Shape/Corner Large Increased"] = MultiverseDimensions.cornerLargeIncreased
        dimensions["Shape/Corner Extra Large"] = MultiverseDimensions.cornerExtraLarge
        dimensions["Shape/Corner Extra Large Increased"] = MultiverseDimensions.cornerExtraLargeIncreased
        dimensions["Shape/Corner Extra Extra Large"] = MultiverseDimensions.cornerExtraExtraLarge
        dimensions["Shape/Corner Full"] = MultiverseDimensions.cornerFull
        dimensions["Radius/Glass Control"] = MultiverseDimensions.glassControl
        dimensions["Radius/Glass Card"] = MultiverseDimensions.glassCard
        dimensions["Radius/Glass Panel"] = MultiverseDimensions.glassPanel
        return dimensions
    }

    /// The export variable names the token objects map.
    public static var mappedNames: Set<String> {
        Set(colors.keys).union(dimensions.keys)
    }
}

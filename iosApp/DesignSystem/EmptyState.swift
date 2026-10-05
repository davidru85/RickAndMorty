import SwiftUI

/// The iOS empty state (`UI_SPEC.md` §6.4, §1.2 `iOS/Empty state`) — the designed placeholder of the
/// Episodes and Favorites destinations.
///
/// It is the exact peer of the Android `EmptyState` (`:core:designsystem`): a 120 pt glass symbol
/// well (`Liquid Glass/Regular`) holding a 48 pt SF Symbol in Portal Glow, a Title 2 heading, a
/// secondary Subheadline body and the `GlassTextButton` action — identical copy on both platforms,
/// per §6.4.
///
/// The copy is passed in as primitives, never literal here (`GUIDELINES.md` §5.7): a caller passes
/// the values the canonical keys carry — `episodes_heading` / `episodes_body` /
/// `browse_characters` for Episodes and `favorites_heading` / `favorites_body` /
/// `browse_characters` for Favorites (`CopyKeys`, `IC-017`), resolved from the iOS resource files
/// that `TASK-060` wires and `TEST-UNIT-036` keeps identical to Android's. The `EmptyStateCopy`
/// constants below name those keys so no call site types them by hand.
///
/// The two visual paths are chosen in `GlassSurface.swift`: the iOS 26+ `glassEffect` on a current
/// OS, the material fallback on iOS 18 or under Reduce Transparency.
///
/// Accessibility (`UI_SPEC.md` §6.4, §9): the illustration is decorative and hidden from the
/// accessibility tree, then the heading, the body and the button are read in that order.
public struct EmptyState: View {
    private let symbol: String
    private let heading: String
    /// The body copy. It is not named `body`: a `View`'s own `body` is the rendering entry point,
    /// and a stored property of that name would collide with it.
    private let message: String
    private let actionLabel: String
    private let action: () -> Void

    public init(
        symbol: String,
        heading: String,
        body: String,
        actionLabel: String,
        action: @escaping () -> Void
    ) {
        self.symbol = symbol
        self.heading = heading
        self.message = body
        self.actionLabel = actionLabel
        self.action = action
    }

    public var body: some View {
        VStack(spacing: MultiverseDimensions.spaceM) {
            illustration
            Text(heading)
                .font(MultiverseType.title2Bold)
                .foregroundStyle(MultiverseLabelColors.primary)
                .multilineTextAlignment(.center)
            Text(message)
                .font(MultiverseType.subheadline)
                .foregroundStyle(MultiverseLabelColors.secondary)
                .multilineTextAlignment(.center)
            GlassTextButton(label: actionLabel, action: action)
                .padding(.top, MultiverseDimensions.spaceXs)
        }
        .frame(width: MultiverseDimensions.emptyStateTextWidth)
        .padding(.horizontal, MultiverseDimensions.spaceL)
    }

    /// The decorative glass symbol well: a 120 pt circle (`Liquid Glass/Regular`, Figma `102:256`) with
    /// a 48 pt Portal Glow symbol. It is hidden from the accessibility tree because the heading is the first thing a
    /// screen reader should read (`UI_SPEC.md` §6.4).
    ///
    /// The 48 pt size comes from the type token `editorialDisplaySize` (`UI_SPEC.md` §6.4 fixes the
    /// illustrated symbol at 48 and §3.4 fixes the editorial display at 40; the well's icon is the
    /// larger of the two, so this is the token that carries the value).
    private var illustration: some View {
        Image(systemName: symbol)
            .font(Font.system(size: MultiverseType.emptyStateSymbolSize, weight: .regular))
            .foregroundStyle(MultiverseBrandColors.portalGlow)
            .frame(
                width: MultiverseDimensions.emptyStateWell,
                height: MultiverseDimensions.emptyStateWell
            )
            .glassSurface(.capsule)
            .accessibilityHidden(true)
    }
}

/// The canonical copy keys the two §6.4 empty states bind (`CopyKeys`, `IC-017`).
///
/// The **values** live in the iOS resource files `TASK-060` wires; this type carries only the names,
/// so a call site names a key instead of typing a string and `TEST-UNIT-036` keeps the two
/// platforms identical.
public enum EmptyStateCopy {
    public static let episodesHeading: CopyKey = .episodesHeading
    public static let episodesBody: CopyKey = .episodesBody
    public static let favoritesHeading: CopyKey = .favoritesHeading
    public static let favoritesBody: CopyKey = .favoritesBody
    public static let browseCharacters: CopyKey = .browseCharacters

    /// The SF Symbols `UI_SPEC.md` §6.4 fixes for the two sections.
    public static let episodesSymbol = "play.tv.fill"
    public static let favoritesSymbol = "heart"
}

#Preview("Episodes placeholder") {
    EmptyState(
        symbol: EmptyStateCopy.episodesSymbol,
        heading: "Episodes are on their way",
        body: "Soon you'll be able to browse every episode, from the Pilot to the latest season.",
        actionLabel: "Browse characters"
    ) {}
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .preferredColorScheme(.dark)
}

#Preview("Favorites empty state") {
    EmptyState(
        symbol: EmptyStateCopy.favoritesSymbol,
        heading: "No favorites yet",
        body: "Tap the heart on a character's page to keep them here.",
        actionLabel: "Browse characters"
    ) {}
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .preferredColorScheme(.dark)
}

#Preview("Empty state — material fallback") {
    EmptyState(
        symbol: EmptyStateCopy.favoritesSymbol,
        heading: "No favorites yet",
        body: "Tap the heart on a character's page to keep them here.",
        actionLabel: "Browse characters"
    ) {}
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .environment(\.multiverseGlassPath, .material)
    .preferredColorScheme(.dark)
}

#Preview("Empty state — Reduce Transparency") {
    EmptyState(
        symbol: EmptyStateCopy.favoritesSymbol,
        heading: "No favorites yet",
        body: "Tap the heart on a character's page to keep them here.",
        actionLabel: "Browse characters"
    ) {}
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .transformEnvironment(\._accessibilityReduceTransparency) { $0 = true }
    .preferredColorScheme(.dark)
}

#Preview("Empty state — largest Dynamic Type") {
    EmptyState(
        symbol: EmptyStateCopy.favoritesSymbol,
        heading: "No favorites yet",
        body: "Tap the heart on a character's page to keep them here.",
        actionLabel: "Browse characters"
    ) {}
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .dynamicTypeSize(.accessibility5)
    .preferredColorScheme(.dark)
}

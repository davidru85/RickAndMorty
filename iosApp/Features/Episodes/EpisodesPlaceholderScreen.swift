import SwiftUI

/// The iOS Episodes placeholder (`UI_SPEC.md` §6.4, `IC-017`, `TASK-056`) — the fourth destination's
/// designed "coming soon" surface, and the peer of the Android `EpisodesPlaceholder`.
///
/// Episodes has no data layer by decision (`DEC-005`), so this is a **stateless UI placeholder**: it
/// renders the copy the specification gives it and holds nothing. That is exactly the shape `DEC-104`
/// exempts from the `S3` domain/presentation requirement — inventing empty layers to satisfy the rule
/// would be fabricated architecture, not delivery.
///
/// The copy is not literal here (`GUIDELINES.md` §5.7): every string is a canonical key the resource
/// files carry (`episodes_heading` / `episodes_body` / `browse_characters`), resolved through
/// `LocalizedCopy` and named by the `EmptyStateCopy` constants, so `TEST-UNIT-036` holds the two
/// platforms byte-identical per locale. The illustration and the well are the shared `EmptyState`
/// (`play.tv.fill`, 120 pt glass well, 48 pt Portal Glow symbol), so the §6.4 presentation cannot
/// drift from Favorites' empty state.
///
/// "Browse characters" is a callback, never a route: the feature does not know where Characters
/// lives, so the shell maps the tap to its own destination and no feature-to-feature edge exists
/// (`REQ-FUNC-008`, `AC-REQ-FUNC-008-2`, ADR-0001). The title is the section's large title in the
/// content, at the same position as Discovery's (`UI_SPEC.md` §6.4), which is why the shell does not
/// also set a `navigationTitle` for this destination.
struct EpisodesPlaceholderScreen: View {
    /// The "Browse characters" action (`AC-REQ-FUNC-008-2`): a selection of Characters, never a push.
    let onBrowseCharacters: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: MultiverseDimensions.spaceM) {
            headline
            EmptyState(
                symbol: EmptyStateCopy.episodesSymbol,
                heading: copy(EmptyStateCopy.episodesHeading),
                body: copy(EmptyStateCopy.episodesBody),
                actionLabel: copy(EmptyStateCopy.browseCharacters),
                action: onBrowseCharacters
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .cosmicCanvas()
    }

    /// The section's large title, at the same position as Discovery's and Favorites' (`UI_SPEC.md`
    /// §6.4, "Large Title, same position as Discovery").
    private var headline: some View {
        Text(copy("nav_episodes"))
            .font(MultiverseType.largeTitleBold)
            .foregroundStyle(MultiverseLabelColors.primary)
            .padding(.horizontal, MultiverseDimensions.spaceL)
            .padding(.top, MultiverseDimensions.spaceM)
    }

    private func copy(_ key: String) -> String {
        LocalizedCopy.shared.text(for: key)
    }
}

#Preview("Episodes placeholder") {
    EpisodesPlaceholderScreen {}
        .preferredColorScheme(.dark)
}

#Preview("Episodes placeholder — material fallback") {
    EpisodesPlaceholderScreen {}
        .environment(\.multiverseGlassPath, .material)
        .preferredColorScheme(.dark)
}

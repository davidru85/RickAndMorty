import MultiverseExplorer
import SwiftUI

/// The iOS Favorites screen (`UI_SPEC.md` §6.4, §6.2, `IC-020`, `TASK-055`).
///
/// It renders the shared `FavoritesUiState` and nothing it derives itself: the favourite cards are
/// the same `IC-016` values Discovery draws, resolved through the **same** `GlassCharacterCard` and
/// the same card cell, so the two lanes cannot diverge (`UI_SPEC.md` §6.4: "using the same cards as
/// Discovery"). The screen owns only the platform concerns — the glass components, the SF Symbols
/// and the accessibility shape — and never reaches a store, a repository or a use case
/// (`ERROR_FLOW.md` §3 invariant 4): every interaction leaves as a `FavoritesIntent`.
///
/// The `IC-020` precedence is rendered as the shared reducer decided it:
///
/// - `Loading` holds the surface and renders nothing of its own: the section is still finding out
///   whether it has favourites, so it never flashes a state it cannot justify (the Android screen's
///   rule, `CONF-79`).
/// - `Empty` is the designed empty state — `favorites_heading` / `favorites_body` /
///   `browse_characters`, with the heart well — shown **only** while the stored set is empty
///   (`AC-REQ-FUNC-006-3`). Its action selects Characters; the callback belongs to the shell, so the
///   feature names no other feature (`REQ-FUNC-008`, ADR-0001).
/// - `Error` is the full-surface error — `error_title`, the `ApiFailure`-specific message and
///   `action_retry` — with a working Retry (`ERROR_FLOW.md` §4, §10).
/// - `Content` is the grid of the same cards Discovery renders.
struct FavoritesScreen: View {
    let state: FavoritesUiState
    let onIntent: (any FavoritesIntent) -> Void
    let onCharacterSelected: (CharacterCardUi) -> Void
    let onBrowseCharacters: () -> Void

    /// The one image seam (`TASK-058`, `UI_SPEC.md` §5.1): a card resolves its portrait through it, so
    /// a test substitutes an in-memory loader and never touches the network. `nil` uses the app's own
    /// pipeline, the only implementation the shipped app resolves.
    private let loader: (any PortraitImageLoading)?

    init(
        state: FavoritesUiState,
        loader: (any PortraitImageLoading)? = nil,
        onIntent: @escaping (any FavoritesIntent) -> Void,
        onCharacterSelected: @escaping (CharacterCardUi) -> Void,
        onBrowseCharacters: @escaping () -> Void
    ) {
        self.state = state
        self.loader = loader
        self.onIntent = onIntent
        self.onCharacterSelected = onCharacterSelected
        self.onBrowseCharacters = onBrowseCharacters
    }

    /// The reader's text size (`UI_SPEC.md` §9): at the accessibility sizes the grid drops to one
    /// column, exactly as Discovery's does.
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(alignment: .leading, spacing: MultiverseDimensions.spaceM) {
            headline
            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .cosmicCanvas()
    }

    /// The section's large title, at the same position as Discovery's (`UI_SPEC.md` §6.4).
    private var headline: some View {
        Text(copy(.navFavorites))
            .font(MultiverseType.largeTitleBold)
            .foregroundStyle(MultiverseLabelColors.primary)
            .padding(.horizontal, MultiverseDimensions.spaceL)
            .padding(.top, MultiverseDimensions.spaceM)
    }

    // MARK: - Content

    /// The `IC-020` precedence rendered: `Empty` and `Error` replace the grid, `Content` renders it,
    /// `Loading` holds the surface.
    @ViewBuilder
    private var content: some View {
        switch state.loadState {
        case is LoadStateEmpty:
            emptyState
        case let error as LoadStateError:
            errorState(failure: error.failure)
        case is LoadStateLoading:
            // Nothing displayable yet: hold the surface rather than flashing a state the section
            // cannot justify (`IC-020`, `CONF-79`).
            Color.clear
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityHidden(true)
        default:
            grid
        }
    }

    /// The designed empty state of `UI_SPEC.md` §6.4: the favourite heart well, the canonical copy
    /// and one action that selects Characters (`AC-REQ-FUNC-006-3`).
    private var emptyState: some View {
        EmptyState(
            symbol: EmptyStateCopy.favoritesSymbol,
            heading: copy(EmptyStateCopy.favoritesHeading),
            body: copy(EmptyStateCopy.favoritesBody),
            actionLabel: copy(EmptyStateCopy.browseCharacters),
            action: onBrowseCharacters
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// The full-surface error: the shared title, the failure's own message and one Retry
    /// (`ERROR_FLOW.md` §4, §10). The message comes from the shared formatter, so the screen never
    /// builds a message from a failure field (`REQ-FUNC-013`, `REQ-UX-008`).
    private func errorState(failure: any ApiFailure) -> some View {
        let message = CharacterPresentation.message(
            DefaultPresentationFormatters.shared.failureMessage(failure: failure)
        )
        return EmptyState(
            symbol: "network.slash",
            heading: copy(CharacterPresentation.key(DefaultPresentationFormatters.shared.failureTitle())),
            body: message,
            actionLabel: copy(CharacterPresentation.key(DefaultPresentationFormatters.shared.retryAction())),
            action: { onIntent(FavoritesIntentRetry.shared) }
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// The favourite grid (`UI_SPEC.md` §6.2): two fixed columns with the 16 pt gutter, and the same
    /// `Tall`-when-`index % 4` is 0 or 3 mix as Discovery so the two lanes never line up. The cards
    /// are the shared `CharacterCardCell`, which is why a favourite is displayed by the same
    /// component contract Discovery's grid uses (`IC-016`, `IC-020`).
    private var grid: some View {
        ScrollView {
            LazyVGrid(columns: gridColumns, spacing: MultiverseDimensions.gridGutter) {
                // Keyed by the canonical id, so removing a favourite never hands its cell to another
                // character (`GAP-031`).
                ForEach(state.items, id: \.gridIdentity) { card in
                    CharacterCardCell(
                        card: card,
                        loader: loader,
                        identifierPrefix: "favorites.card",
                        action: { onCharacterSelected(card) }
                    )
                }
            }
            // Discovery's 16 pt margins (Figma `102:375`): two 177 pt cards and a 16 pt gutter fill
            // the 402 pt width.
            .padding(.horizontal, MultiverseDimensions.spaceL)
            .padding(.vertical, MultiverseDimensions.spaceM)
        }
    }

    private var gridColumns: [GridItem] {
        // `UI_SPEC.md` §9: the grid drops to one column at the largest accessibility sizes.
        if dynamicTypeSize.isAccessibilitySize {
            return [GridItem(.flexible())]
        }
        return [GridItem](repeating: GridItem(.flexible(), spacing: MultiverseDimensions.gridGutter), count: 2)
    }

    // MARK: - Helpers

    private func copy(_ key: String) -> String {
        LocalizedCopy.shared.text(for: key)
    }

    private func copy(_ key: CopyKey) -> String {
        LocalizedCopy.shared.text(for: key)
    }
}

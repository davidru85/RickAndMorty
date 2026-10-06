import MultiverseExplorer
import SwiftUI

/// The states the feature previews render (`GUIDELINES.md` §6.5, `TASK-140`).
///
/// Each one is built from the shared Kotlin contract its screen consumes (`IC-016`, `IC-018`…`IC-020`,
/// `IC-023`), so a preview shows exactly what a state holder could hand the screen, and nothing reaches the
/// network, a store or the shared graph (`DESIGN.md` §8). Names and places are API data, not copy: every
/// label a screen shows still resolves through `LocalizedCopy`. The arrays are built by appending because
/// the repository's formatter and linter disagree about a multiline literal's trailing comma (`GAP-030`).
enum PreviewFixtures {
    // MARK: - Cards

    /// One card per status tone, so the capsule's three colours show (`UI_SPEC.md` §3.2).
    static var cards: [CharacterCardUi] {
        var cards: [CharacterCardUi] = []
        cards.append(card(id: "1", name: "Rick Sanchez", status: CharacterStatusAlive.shared))
        cards.append(card(id: "2", name: "Morty Smith", status: CharacterStatusAlive.shared))
        cards.append(card(id: "8", name: "Adjudicator Rick", status: CharacterStatusDead.shared))
        cards.append(card(id: "7", name: "Abradolf Lincler", status: CharacterStatusUnknown.shared))
        return cards
    }

    /// A portrait URL; it only ever reaches the preview loader.
    static func portraitUrl(id: String) -> String {
        "https://example.invalid/avatar/\(id).jpeg"
    }

    /// A card as the shared mapping builds it (`IC-016`).
    static func card(id: String, name: String, status: any CharacterStatus) -> CharacterCardUi {
        CharacterCardUi(
            id: id,
            name: name,
            species: DisplayTextData(value: "Human"),
            status: status,
            statusLabel: DefaultPresentationFormatters.shared.statusKey(status: status),
            imageUrl: portraitUrl(id: id)
        )
    }

    // MARK: - Discovery (`IC-018`)

    static var discoveryLoading: CharacterListUiState {
        discovery(items: [], totalCount: nil, loadState: LoadStateLoading.shared)
    }

    static var discoveryContent: CharacterListUiState {
        discovery(items: cards, totalCount: 826, loadState: LoadStateContent.shared)
    }

    static var discoveryAppending: CharacterListUiState {
        discovery(items: cards, totalCount: 826, loadState: LoadStateContent.shared, isAppending: true)
    }

    /// Content from an expired cache entry, under the "Showing saved results" banner (`DEC-124`).
    static var discoveryStale: CharacterListUiState {
        discovery(items: cards, totalCount: 826, loadState: LoadStateContent.shared, isStale: true)
    }

    /// A search that matched nothing: the message names the query (`AC-REQ-FUNC-010-2`), and the count
    /// stays unknown, because the server establishes none for an empty result (`AC-REQ-FUNC-001-3`).
    static var discoveryEmpty: CharacterListUiState {
        discovery(items: [], totalCount: nil, loadState: LoadStateEmpty.shared, query: "Xyzzy")
    }

    static var discoveryError: CharacterListUiState {
        discovery(items: [], totalCount: nil, loadState: LoadStateError(failure: ApiFailureOffline.shared))
    }

    private static func discovery(
        items: [CharacterCardUi],
        totalCount: Int32?,
        loadState: any LoadState,
        query: String = "",
        isAppending: Bool = false,
        isStale: Bool = false
    ) -> CharacterListUiState {
        CharacterListUiState(
            filter: CharacterFilter(query: query, status: StatusFilter.all),
            items: items,
            totalCount: totalCount.map { KotlinInt(int: $0) },
            loadState: loadState,
            isAppending: isAppending,
            isStale: isStale,
            contentFailure: nil,
            isRefreshing: false
        )
    }

    // MARK: - Detail (`IC-019`)

    /// The list-provided card the navigation hand-off carries in.
    static var detailHeader: CharacterCardUi {
        card(id: "1", name: "Rick Sanchez", status: CharacterStatusAlive.shared)
    }

    /// The loaded detail (`UI_SPEC.md` §6.3): the three stats and the three info rows in their fixed order.
    static var detailContent: CharacterDetailUiState {
        detail(header: detailHeader, loadState: LoadStateContent.shared, loaded: true, isFavorite: true)
    }

    /// The first frame: the header is there while the detail loads (`AC-REQ-FUNC-002-1`).
    static var detailLoading: CharacterDetailUiState {
        detail(header: detailHeader, loadState: LoadStateLoading.shared, loaded: false)
    }

    /// A failure with the header known: the inline retry replaces the info rows (`AC-REQ-FUNC-002-3`).
    static var detailInlineError: CharacterDetailUiState {
        detail(header: detailHeader, loadState: LoadStateError(failure: ApiFailureOffline.shared), loaded: false)
    }

    /// A failure with nothing known: the full-surface error (`DEC-131`).
    static var detailError: CharacterDetailUiState {
        detail(header: nil, loadState: LoadStateError(failure: ApiFailureOffline.shared), loaded: false)
    }

    private static func detail(
        header: CharacterCardUi?,
        loadState: any LoadState,
        loaded: Bool,
        isFavorite: Bool = false
    ) -> CharacterDetailUiState {
        CharacterDetailUiState(
            header: header,
            gender: loaded ? CopyKeys.shared.GENDER_MALE : nil,
            episodeCount: loaded ? KotlinInt(int: 51) : nil,
            dimension: loaded ? "C-137" : nil,
            info: loaded ? infoRows : [],
            isFavorite: isFavorite,
            loadState: loadState
        )
    }

    private static var infoRows: [InfoRowUi] {
        var rows: [InfoRowUi] = []
        rows.append(row(.origin, CopyKeys.shared.DETAIL_INFO_ORIGIN, "Earth (C-137)"))
        rows.append(row(.lastknownlocation, CopyKeys.shared.DETAIL_INFO_LAST_KNOWN_LOCATION, "Citadel of Ricks"))
        rows.append(row(.firstseenin, CopyKeys.shared.DETAIL_INFO_FIRST_SEEN_IN, "Pilot"))
        return rows
    }

    private static func row(_ kind: InfoRowKind, _ copyKey: Any, _ value: String) -> InfoRowUi {
        InfoRowUi(kind: kind, copyKey: copyKey, value: DisplayTextData(value: value))
    }

    // MARK: - Favorites (`IC-020`)

    static var favoritesContent: FavoritesUiState {
        FavoritesUiState(items: Array(cards.prefix(3)), loadState: LoadStateContent.shared)
    }

    static var favoritesEmpty: FavoritesUiState {
        FavoritesUiState(items: [], loadState: LoadStateEmpty.shared)
    }

    static var favoritesError: FavoritesUiState {
        FavoritesUiState(items: [], loadState: LoadStateError(failure: ApiFailureOffline.shared))
    }

    /// The first store emission is awaited: the section holds its surface (`CONF-79`).
    static var favoritesLoading: FavoritesUiState {
        FavoritesUiState(items: [], loadState: LoadStateLoading.shared)
    }

    // MARK: - Settings (`IC-023`)

    /// Sounds on, GraphQL in force and favourites saved, so every control is in its non-default state.
    static var settingsConfigured: SettingsUiState {
        SettingsUiState(
            soundsEnabled: true,
            remoteProtocol: RemoteProtocol.graphql,
            canDeleteFavorites: true,
            isConfirmingDelete: false
        )
    }
}

// MARK: - Screen previews

extension DiscoveryScreen {
    /// The screen in a navigation stack, which its large title needs, with the preview loader.
    static func preview(_ state: CharacterListUiState, _ variant: PreviewVariant = .standard) -> some View {
        NavigationStack {
            DiscoveryScreen(
                state: state,
                loader: PreviewPortraitLoader.loaded,
                onIntent: { _ in },
                onOpenDetail: { _ in }
            )
        }
        .previewVariant(variant)
    }
}

extension CharacterDetailScreen {
    /// The screen with the preview loader drawing its hero and backdrop.
    static func preview(_ state: CharacterDetailUiState, _ variant: PreviewVariant = .standard) -> some View {
        CharacterDetailScreen(
            state: state,
            loader: PreviewPortraitLoader.loaded,
            onIntent: { _ in },
            onBack: {},
            onShare: {}
        )
        .previewVariant(variant)
    }
}

extension FavoritesScreen {
    /// The screen with the preview loader drawing its cards.
    static func preview(_ state: FavoritesUiState, _ variant: PreviewVariant = .standard) -> some View {
        FavoritesScreen(
            state: state,
            loader: PreviewPortraitLoader.loaded,
            onIntent: { _ in },
            onCharacterSelected: { _ in },
            onBrowseCharacters: {}
        )
        .previewVariant(variant)
    }
}

/// The portrait seam the previews install (`TASK-058`, `UI_SPEC.md` §5.1): in memory, with no network and
/// no disk, and one fixed outcome per instance so each portrait state can be previewed.
@MainActor
final class PreviewPortraitLoader: PortraitImageLoading {
    private enum Outcome {
        case loaded, failed, pending
    }

    /// Every portrait has loaded; a token gradient stands in for the picture.
    static let loaded = PreviewPortraitLoader(.loaded)

    /// Every load fails, so the portrait shows the portal mark at 40 %.
    static let failed = PreviewPortraitLoader(.failed)

    /// No load ends while the preview is open, so the portrait keeps its shimmering placeholder.
    static let pending = PreviewPortraitLoader(.pending)

    private let outcome: Outcome

    private init(_ outcome: Outcome) {
        self.outcome = outcome
    }

    func cachedImage(for url: String) -> Image? {
        outcome == .loaded ? Self.portrait : nil
    }

    func image(for url: String) async -> Image? {
        switch outcome {
        case .loaded:
            return Self.portrait
        case .failed:
            return nil
        case .pending:
            try? await Task.sleep(for: .seconds(3_600))
            return nil
        }
    }

    private static var portrait: Image {
        let size = CGSize(width: MultiverseDimensions.glassCardWidth, height: MultiverseDimensions.glassCardHeight)
        let gradient = Gradient(colors: [MultiverseColors.tertiaryContainer, MultiverseColors.primaryContainer])
        return Image(size: size) { context in
            context.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .linearGradient(gradient, startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height))
            )
        }
    }
}

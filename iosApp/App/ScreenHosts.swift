import MultiverseExplorer
import SwiftUI

/// The hosts that bind a feature screen to the shared graph (`TASK-055`, `DEC-091`).
///
/// Each host owns the one state holder its screen needs, built from the same graph the Android shell
/// starts, and passes the screen a plain state value plus an intent closure. A screen therefore never
/// resolves a dependency and never sees Koin, which is what keeps a feature independent of the
/// composition root (`CONTRACTS.md` R2) and makes every screen renderable from a state alone in a
/// test.
///
/// The holders are `@StateObject`s, so a SwiftUI re-render does not rebuild one — rebuilding would
/// start a second pager and a second store for the same screen.

/// Discovery (`IC-018`).
struct DiscoveryHost: View {
    @StateObject private var holder: DiscoveryStateHolder
    private let onOpenDetail: (CharacterCardUi) -> Void

    init(onOpenDetail: @escaping (CharacterCardUi) -> Void) {
        self.onOpenDetail = onOpenDetail
        let scope = MultiverseBootstrap.shared.screenScope()
        _holder = StateObject(
            wrappedValue: DiscoveryStateHolder(
                pager: MultiverseBootstrap.shared.characterPager(scope: scope),
                initialFilter: DiscoveryHost.defaultFilter
            )
        )
    }

    /// The first filter: everything, which is what `StatusFilter.All` means (`AC-REQ-FUNC-004-2`).
    private static var defaultFilter: CharacterFilter {
        CharacterFilter(query: "", status: StatusFilter.all)
    }

    var body: some View {
        DiscoveryScreen(
            state: holder.state,
            onIntent: { holder.onIntent($0) },
            onOpenDetail: onOpenDetail,
            onRefresh: { await holder.refresh() }
        )
    }
}

/// One character's detail (`IC-019`).
///
/// Back dismisses the pushed screen, and Share presents the system sheet with the character's line
/// (`DEC-125`); both controls were empty closures before.
struct DetailHost: View {
    @StateObject private var holder: DetailStateHolder
    @Environment(\.dismiss) private var dismiss
    @State private var sharing: ShareItem?

    init(card: CharacterCardUi) {
        let resolved = MultiverseBootstrap.shared.characterDetailDependencies()
        _holder = StateObject(
            wrappedValue: DetailStateHolder(
                id: card.id,
                header: card,
                getDetails: resolved.getDetails,
                toggleFavorite: resolved.toggleFavorite,
                observeFavoriteIds: resolved.observeFavoriteIds,
                enrich: true
            )
        )
    }

    var body: some View {
        CharacterDetailScreen(
            state: holder.state,
            onIntent: { holder.onIntent($0) },
            onBack: { dismiss() },
            onShare: {
                // Share names the character, so it waits for a header; the list's card always
                // provides one on iOS, and a loaded detail builds one otherwise.
                if let header = holder.state.header {
                    sharing = ShareItem(text: CharacterShare.text(for: header))
                }
            }
        )
        .sheet(item: $sharing) { item in
            ShareSheet(item: item)
                .presentationDetents([.medium, .large])
        }
    }
}

/// The favourites section (`IC-020`).
struct FavoritesHost: View {
    @StateObject private var holder: FavoritesStateHolder
    private let onBrowse: () -> Void
    private let onOpenDetail: (CharacterCardUi) -> Void

    init(onBrowse: @escaping () -> Void, onOpenDetail: @escaping (CharacterCardUi) -> Void) {
        self.onBrowse = onBrowse
        self.onOpenDetail = onOpenDetail
        let resolved = MultiverseBootstrap.shared.favoritesDependencies()
        _holder = StateObject(
            wrappedValue: FavoritesStateHolder(
                observeFavoriteIds: resolved.observeFavoriteIds,
                resolveFavoriteCards: resolved.resolveFavoriteCards
            )
        )
    }

    var body: some View {
        FavoritesScreen(
            state: holder.state,
            onIntent: { holder.onIntent($0) },
            onCharacterSelected: onOpenDetail,
            onBrowseCharacters: onBrowse
        )
    }
}

/// The settings screen (`IC-023`, `TASK-077`).
struct SettingsHost: View {
    @StateObject private var holder: SettingsStateHolder

    init() {
        let resolved = MultiverseBootstrap.shared.settingsDependencies()
        _holder = StateObject(
            wrappedValue: SettingsStateHolder(
                observeAppSettings: resolved.observeAppSettings,
                updateAppSettings: resolved.updateAppSettings,
                observeFavoriteIds: resolved.observeFavoriteIds,
                clearFavorites: resolved.clearFavorites
            )
        )
    }

    var body: some View {
        SettingsScreen(
            state: holder.state,
            onIntent: { holder.onIntent($0) }
        )
    }
}

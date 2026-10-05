@testable import MultiverseApp
import MultiverseExplorer
import SnapshotTesting
import SwiftUI
import XCTest

/// `TEST-UI-014` and `TEST-UI-016` on iOS: the screens' committed baselines (`REQ-UX-006`,
/// `AC-REQ-UX-006-1`, `REQ-UX-009`, `TASK-059`, `GAP-031`).
///
/// The design-system baselines pin the components; these pin what `AC-REQ-UX-006-1` is about — the
/// Discovery grid in two columns at the default text size and in **one** column at the largest
/// accessibility size, and the detail at that size without clipping. Every screen is rendered in a
/// real window (`HostedRendering`), so a lazy grid that keeps the wrong cells (`GAP-031`) or a hero
/// that covers the screen shows in the image. Portraits come from the in-memory stub; no network is
/// involved.
///
/// The baselines were recorded on iPhone 17, iOS 27.0 — the device and runtime the `ios` job pins — from
/// states that exist and are correct, and a missing reference fails (`TESTING.md` §8.2).
@MainActor
final class ScreenSnapshotTests: XCTestCase {
    func test_TEST_UI_014_screenBaselines() {
        let names = ["Rick Sanchez", "Morty Smith", "Summer Smith"]
        let cards = (1...6).map { card(id: "\($0)", name: names[($0 - 1) % names.count]) }
        let content = CharacterListUiState(
            filter: CharacterFilter(query: "", status: StatusFilter.all),
            items: cards,
            totalCount: KotlinInt(int: 826),
            loadState: LoadStateContent.shared,
            isAppending: false,
            isStale: false
        )
        record("discovery-grid", dynamicTypeSize: .large) {
            DiscoveryScreen(state: content, loader: PortraitImageStub(), onIntent: { _ in }, onOpenDetail: { _ in })
        }
        // `AC-REQ-UX-006-1`: at the largest accessibility size the grid is one column.
        record("discovery-grid-accessibility-text", dynamicTypeSize: .accessibility5) {
            DiscoveryScreen(state: content, loader: PortraitImageStub(), onIntent: { _ in }, onOpenDetail: { _ in })
        }
        let detail = CharacterDetailUiState(
            header: cards[0],
            episodeCount: KotlinInt(int: 51),
            dimension: "Dimension C-137",
            info: detailRows(),
            isFavorite: true,
            loadState: LoadStateContent.shared
        )
        record("detail", dynamicTypeSize: .large) {
            detailScreen(detail)
        }
        record("detail-accessibility-text", dynamicTypeSize: .accessibility5) {
            detailScreen(detail)
        }
        // The designed empty favourites state at the phone width (`UI_SPEC.md` §6.4).
        record("favorites-empty", dynamicTypeSize: .large) {
            FavoritesScreen(
                state: FavoritesUiState(items: [], loadState: LoadStateEmpty.shared),
                loader: PortraitImageStub(),
                onIntent: { _ in },
                onCharacterSelected: { _ in },
                onBrowseCharacters: {}
            )
        }
    }

    /// `TEST-UI-016` on iOS: the seven rendered states of `ERROR_FLOW.md` §12, each a committed
    /// baseline, so the state model is cross-checked against images on both platforms
    /// (`AC-REQ-UX-009-1`, `TASK-064`). The stale banner over content is the combined
    /// stale-plus-failed-refresh case, and `detail` above is the enrichment-absent partial detail.
    func test_TEST_UI_016_stateBaselines() {
        let names = ["Rick Sanchez", "Morty Smith", "Summer Smith"]
        let cards = (1...6).map { card(id: "\($0)", name: names[($0 - 1) % names.count]) }
        let list = { (items: [CharacterCardUi], loadState: any LoadState, appending: Bool, stale: Bool) in
            CharacterListUiState(
                filter: CharacterFilter(query: items.isEmpty ? "zzz" : "", status: StatusFilter.all),
                items: items,
                totalCount: KotlinInt(int: 826),
                loadState: loadState,
                isAppending: appending,
                isStale: stale
            )
        }
        let discovery = { (state: CharacterListUiState) in
            DiscoveryScreen(state: state, loader: PortraitImageStub(), onIntent: { _ in }, onOpenDetail: { _ in })
        }
        record("discovery-loading", dynamicTypeSize: .large) {
            discovery(list([], LoadStateLoading.shared, false, false))
        }
        // Two cards, so the paging indicator after the last row is inside the frame.
        record("discovery-appending", dynamicTypeSize: .large) {
            discovery(list(Array(cards.prefix(2)), LoadStateContent.shared, true, false))
        }
        record("discovery-empty", dynamicTypeSize: .large) {
            discovery(list([], LoadStateEmpty.shared, false, false))
        }
        record("discovery-error-offline", dynamicTypeSize: .large) {
            discovery(list([], LoadStateError(failure: ApiFailureOffline.shared), false, false))
        }
        record("discovery-stale", dynamicTypeSize: .large) {
            discovery(list(cards, LoadStateContent.shared, false, true))
        }
        let failedDetail = { (header: CharacterCardUi?) in
            CharacterDetailUiState(
                header: header,
                episodeCount: nil,
                dimension: nil,
                info: [],
                isFavorite: false,
                loadState: LoadStateError(failure: ApiFailureOffline.shared)
            )
        }
        record("detail-error-with-header", dynamicTypeSize: .large) {
            detailScreen(failedDetail(cards[0]))
        }
        record("detail-error-without-header", dynamicTypeSize: .large) {
            detailScreen(failedDetail(nil))
        }
    }

    /// Renders the screen in a real window, as the device draws it — after layout, the portrait's
    /// load and the appearance transitions — and compares that frame with the committed baseline.
    /// Snapshot-testing's off-screen SwiftUI path draws neither, which left these screens black.
    private func record(
        _ name: String,
        dynamicTypeSize: DynamicTypeSize,
        @ViewBuilder content: () -> some View,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let view =
            content()
            .background(MultiverseBrandColors.spaceBlack)
            // The material path: these baselines pin layout — one column, no clipping, a visible hero —
            // and a full-window Liquid Glass render differs between runs on the same simulator, while
            // the glass and fallback paths are each pinned per component by `DesignSystemSnapshotTests`.
            .environment(\.multiverseGlassPath, .material)
            .environment(\.dynamicTypeSize, dynamicTypeSize)
        guard let image = try? HostedRendering.render(UIHostingController(rootView: view)) else {
            XCTFail("\(name) must render in a window", file: file, line: line)
            return
        }
        assertSnapshot(
            of: image,
            // The same perceptual tolerance as the design-system baselines: host GPUs differ below
            // what a reader sees, and a changed layout, colour or text still fails.
            as: .image(precision: 0.99, perceptualPrecision: 0.98),
            named: name,
            record: false,
            file: file,
            testName: "screenBaselines",
            line: line
        )
    }

    private func detailScreen(_ state: CharacterDetailUiState) -> CharacterDetailScreen {
        CharacterDetailScreen(state: state, loader: PortraitImageStub(), onIntent: { _ in }, onBack: {}, onShare: {})
    }

    private func detailRows() -> [InfoRowUi] {
        var rows: [InfoRowUi] = []
        rows.append(
            InfoRowUi(kind: InfoRowKind.origin, copyKey: CopyKeys.shared.DETAIL_INFO_ORIGIN, value: "Earth (C-137)")
        )
        rows.append(
            InfoRowUi(
                kind: InfoRowKind.lastknownlocation,
                copyKey: CopyKeys.shared.DETAIL_INFO_LAST_KNOWN_LOCATION,
                value: "Citadel of Ricks"
            )
        )
        return rows
    }

    private func card(id: String, name: String) -> CharacterCardUi {
        CharacterCardUi(
            id: id,
            name: name,
            species: DisplayTextData(value: "Human"),
            status: CharacterStatusAlive.shared,
            statusLabel: CopyKeys.shared.STATUS_ALIVE,
            imageUrl: "https://rickandmortyapi.com/api/character/avatar/\(id).jpeg"
        )
    }
}

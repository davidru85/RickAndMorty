import MultiverseExplorer
import SwiftUI

/// The iOS Discovery screen (`UI_SPEC.md` §6.2, §8, `IC-018`, `TASK-055`).
///
/// It renders the shared `CharacterListUiState` and nothing it derives itself: the headline's count
/// comes from the state's `totalCount` (the server's `info.count`), the four filter options are the
/// shared `StatusFilter` cases with their canonical copy keys, and the grid draws each card from the
/// `IC-016` values the state carries (`CONTRACTS.md` R2). The screen owns only the platform
/// concerns — the glass components, the SF Symbols and the accessibility shape — and never reaches a
/// pager, a repository or a use case (`ERROR_FLOW.md` §3 invariant 4): every interaction leaves as a
/// `CharacterListIntent`.
///
/// The content order is Figma `29:381`'s (`DEC-138`): the system large title, which collapses into the
/// inline title on scroll, then the count line, the glass search field and the glass segmented control
/// as the first rows of the scrolling content, then the grid on 16 pt margins — or, in its place, the
/// designed empty or error surface. Every state in `ERROR_FLOW.md` §8 renders here: the initial skeleton, the paging
/// indicator, the empty search, the full-surface error with Retry and the stale banner over content.
///
/// The search field carries no microphone (`REQ-SEC-004`, `DEC-002`): voice search is deferred, so
/// the affordance is not rendered and no speech permission is requested.
struct DiscoveryScreen: View {
    let state: CharacterListUiState
    let onIntent: (any CharacterListIntent) -> Void
    let onOpenDetail: (CharacterCardUi) -> Void

    /// The manual refresh (`REQ-FUNC-012`, `DEC-134`): `.refreshable` awaits it, so it returns when
    /// the shared refresh ends. A preview or a case that does not refresh passes nothing.
    let onRefresh: () async -> Void

    /// Every tap on the status selector, the option already selected included, so the caller can answer the
    /// tap itself — the shell plays the selection sound (`REQ-FUNC-036`, `AC-REQ-FUNC-036-2`). The filter
    /// change is still the intent's; a reset the app makes ("Clear filters") is not a tap and is not
    /// reported.
    let onStatusSelected: (StatusFilter) -> Void

    /// The one image seam (`TASK-058`, `UI_SPEC.md` §5.1): a card's portrait resolves through it, so a
    /// test substitutes an in-memory loader and never touches the network. `nil` uses the app's own
    /// pipeline, which is the only implementation the shipped app resolves.
    private let loader: (any PortraitImageLoading)?

    init(
        state: CharacterListUiState,
        loader: (any PortraitImageLoading)? = nil,
        onIntent: @escaping (any CharacterListIntent) -> Void,
        onOpenDetail: @escaping (CharacterCardUi) -> Void,
        onRefresh: @escaping () async -> Void = {},
        onStatusSelected: @escaping (StatusFilter) -> Void = { _ in }
    ) {
        self.state = state
        self.loader = loader
        self.onIntent = onIntent
        self.onOpenDetail = onOpenDetail
        self.onRefresh = onRefresh
        self.onStatusSelected = onStatusSelected
    }

    /// The typed query, held by the view only until the intent leaves.
    ///
    /// The reducer owns the 300 ms debounce and the page-1 reset, so nothing here decides when a
    /// request happens (`REQ-FUNC-003`, `AC-REQ-FUNC-003-2`); the field mirrors the state's filter so
    /// a "Clear filters" action is reflected immediately.
    @State private var query: String = ""

    /// The last count the line showed, so the hidden line keeps its text and its height (`TASK-135`).
    @State private var lastCount: Int32 = 0

    /// The reader's text size (`UI_SPEC.md` §9): at the accessibility sizes the grid drops to one
    /// column.
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: MultiverseDimensions.spaceM) {
                countLine
                searchField
                filterRow
                content
                    .padding(.top, MultiverseDimensions.spaceXs)
            }
            // Figma's 16 pt margins: two 177 pt cards and a 16 pt gutter fill the 402 pt width.
            .padding(.horizontal, MultiverseDimensions.spaceL)
            .padding(.bottom, MultiverseDimensions.spaceL)
        }
        // Pull to refresh revalidates page 1 over the network; the spinner holds until it ends.
        .refreshable { await onRefresh() }
        .safeAreaInset(edge: .bottom) {
            // Over the grid only, as before the header joined the scroll: the empty and error surfaces
            // state their own recovery.
            if let notice = DiscoveryNotice.make(for: state), showsGrid {
                noticeBanner(notice)
            }
        }
        // The system large title (`UI_SPEC.md` §4.2, §6.2): it collapses into the inline title as the
        // content scrolls under it.
        .navigationTitle(copy(.navCharacters))
        .navigationBarTitleDisplayMode(.large)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .cosmicCanvas()
        .onAppear {
            query = state.filter.query
            if let count = state.totalCount { lastCount = count.int32Value }
        }
        .onChange(of: state.filter.query) { _, next in query = next }
        .onChange(of: state.totalCount?.int32Value) { _, next in
            if let next { lastCount = next }
        }
    }

    // MARK: - Search field

    /// The glass search field of `UI_SPEC.md` §4.2: the spec's placeholder, no mic (`DEC-002`).
    private var searchField: some View {
        GlassSearchField(
            text: Binding(
                get: { query },
                set: { next in
                    query = next
                    onIntent(CharacterListIntentQueryChanged(query: next))
                }
            ),
            placeholder: copy(.searchCharacters)
        )
    }

    // MARK: - Count line

    /// The count line under the large title (`AC-REQ-FUNC-001-3`). The number is the shared formatter's
    /// output over the state's `totalCount`, so both platforms render the same template.
    ///
    /// It **keeps its line** while the count is unknown (`TASK-135`, `UI_SPEC.md` §6.2): a filter change
    /// resets the total until the new page answers, and removing the line moved the search field, the
    /// segments and the grid up and back. While unknown it keeps the last count's text — `0` before any —
    /// at zero opacity and hidden from VoiceOver, so it holds the same height and is never read stale.
    private var countLine: some View {
        let known = state.totalCount != nil
        return Text(countLine(count: state.totalCount?.int32Value ?? lastCount))
            .font(MultiverseType.subheadline)
            .foregroundStyle(MultiverseLabelColors.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .opacity(known ? 1 : 0)
            .accessibilityHidden(!known)
            .animation(.easeInOut(duration: DiscoveryLayout.countLineFade), value: known)
    }

    private func countLine(count: Int32) -> String {
        let rendered = DefaultPresentationFormatters.shared.charactersCount(count: count)
        return LocalizedCopy.shared.text(for: .charactersCount, arguments: [.text(rendered)])
    }

    // MARK: - Filters

    /// The four single-select options (`AC-REQ-FUNC-004-2`), `All` by default. The options are built
    /// from the shared `StatusFilter` cases and the canonical keys, so the row cannot drift from the
    /// shared filter vocabulary.
    private var filterRow: some View {
        GlassSegmentedControl(
            selection: Binding(
                get: { CharacterPresentation.filterIdentifier(state.filter.status) },
                // The control assigns its selection on every tap, the selected option's too.
                set: { identifier in
                    Self.selectStatus(identifier, onIntent: onIntent, onStatusSelected: onStatusSelected)
                }
            ),
            options: filterOptions
        )
    }

    private var filterOptions: [GlassSegmentedControl.Option] {
        var options: [GlassSegmentedControl.Option] = []
        let all = CharacterPresentation.filterIdentifier(StatusFilter.all)
        let alive = CharacterPresentation.filterIdentifier(StatusFilter.alive)
        let dead = CharacterPresentation.filterIdentifier(StatusFilter.dead)
        options.append(.init(id: all, label: copy(.filterAll)))
        options.append(.init(id: alive, label: copy(.statusAlive)))
        options.append(.init(id: dead, label: copy(.statusDead)))
        options.append(
            .init(id: CharacterPresentation.filterIdentifier(StatusFilter.unknown), label: copy(.valueUnknown))
        )
        return options
    }

    /// A tap on the status selector (`AC-REQ-FUNC-036-2`): the option's intent, then the report of the tap,
    /// on every tap, the option already selected included. An identifier that names no status does
    /// neither.
    static func selectStatus(
        _ identifier: String,
        onIntent: (any CharacterListIntent) -> Void,
        onStatusSelected: (StatusFilter) -> Void
    ) {
        guard let status = CharacterPresentation.statusFilter(identifier) else { return }
        onIntent(CharacterListIntentStatusSelected(status: status))
        onStatusSelected(status)
    }

    // MARK: - Content

    /// The `IC-018` precedence rendered: `Empty` and `Error` replace the grid, `Loading` and
    /// `Content` render it with their own overlay (`ERROR_FLOW.md` §8).
    @ViewBuilder
    private var content: some View {
        switch state.loadState {
        case is LoadStateEmpty:
            emptyState
        case let error as LoadStateError:
            errorState(failure: error.failure)
        default:
            grid
        }
    }

    /// The stated empty-results surface (`UI_SPEC.md` §8, iOS column: "Same content in
    /// `ContentUnavailableView`"): the portal logo at 40 % (`DEC-139`), the message with the active
    /// query and "Clear filters" (`AC-REQ-FUNC-010-2`).
    private var emptyState: some View {
        ContentUnavailableView {
            Label {
                Text(emptySearchMessage)
            } icon: {
                PortalLogo()
                    .frame(width: DiscoveryLayout.emptyMarkSize, height: DiscoveryLayout.emptyMarkSize)
                    .opacity(0.4)
            }
        } actions: {
            GlassTextButton(label: copy(.actionClearFilters)) {
                // Both dimensions in one intent (`DEC-129`); the field and the segments follow the
                // state's filter.
                onIntent(CharacterListIntentClearFilters.shared)
            }
        }
        .frame(maxWidth: .infinity, minHeight: DiscoveryLayout.fullSurfaceMinHeight)
    }

    private var emptySearchMessage: String {
        LocalizedCopy.shared.text(for: .emptySearchMessage, arguments: [.text(state.filter.query)])
    }

    /// The full-surface error (`UI_SPEC.md` §8, iOS column: "`ContentUnavailableView` + Retry glass
    /// button"): the shared title, the failure's own message and one Retry (`ERROR_FLOW.md` §4, §10).
    private func errorState(failure: any ApiFailure) -> some View {
        let message = CharacterPresentation.message(
            DefaultPresentationFormatters.shared.failureMessage(failure: failure)
        )
        let title = copy(CharacterPresentation.key(DefaultPresentationFormatters.shared.failureTitle()))
        return ContentUnavailableView {
            Label(title, systemImage: "network.slash")
        } description: {
            Text(message)
        } actions: {
            GlassTextButton(
                label: copy(CharacterPresentation.key(DefaultPresentationFormatters.shared.retryAction()))
            ) {
                onIntent(CharacterListIntentRetry.shared)
            }
        }
        .frame(maxWidth: .infinity, minHeight: DiscoveryLayout.fullSurfaceMinHeight)
    }

    /// Whether the content is the grid rather than the empty or error surface that replaces it.
    private var showsGrid: Bool {
        !(state.loadState is LoadStateEmpty) && !(state.loadState is LoadStateError)
    }

    /// The grid of glass cards, with the paging indicator as its last item and the non-blocking notice
    /// at the bottom (`UI_SPEC.md` §8). Every iOS card is the same 177 × 236 pt (`UI_SPEC.md` §4.2).
    private var grid: some View {
        VStack(alignment: .leading, spacing: MultiverseDimensions.spaceM) {
            LazyVGrid(columns: gridColumns, spacing: MultiverseDimensions.gridGutter) {
                if state.loadState is LoadStateLoading {
                    skeletonGrid
                } else {
                    cardGrid
                }
            }
            if state.isAppending { pagingIndicator }
        }
    }

    /// The paging trigger of `API_SPECS.md` §8 (`TASK-136`): a card within
    /// `DiscoveryLayout.prefetchDistance` of the end requests the next page as it appears, and the
    /// reducer's own guards — in flight, end reached, failure — decide what happens. The cards are the
    /// grid's lazy cells, so they appear as the user scrolls to them, and each page brings new ones, so
    /// the trigger re-arms; a trigger in the non-lazy stack around the grid appeared with the first render
    /// and never again. Only `Content` requests a page, so neither a loading nor an error surface can.
    private func requestNextPageIfNearEnd(_ index: Int) {
        guard state.loadState is LoadStateContent,
            index >= state.items.count - 1 - DiscoveryLayout.prefetchDistance
        else { return }
        onIntent(CharacterListIntentLoadNextPage.shared)
    }

    private var gridColumns: [GridItem] {
        // `UI_SPEC.md` §9: the grid drops to one column at the largest accessibility sizes.
        if dynamicTypeSize.isAccessibilitySize {
            return [GridItem(.flexible())]
        }
        // Built with the repeating initialiser rather than as a multiline literal: `GAP-030` makes a
        // multiline collection literal unsatisfiable for both tools, and the two-column grid is the
        // same shape Favorites uses.
        return [GridItem](repeating: GridItem(.flexible(), spacing: MultiverseDimensions.gridGutter), count: 2)
    }

    /// Six skeleton cards while no load has completed (`UI_SPEC.md` §8).
    private var skeletonGrid: some View {
        // The skeletons carry identities no card can have, so the grid replaces them when content
        // arrives instead of keeping their cells (`GAP-031`).
        ForEach(DiscoveryLayout.skeletonIdentities, id: \.self) { _ in
            GlassCharacterCard(
                name: "",
                species: "",
                statusTone: .unknown,
                statusLabel: "",
                portrait: CharacterPresentation.placeholderPortrait()
            )
            .redacted(reason: .placeholder)
            .accessibilityHidden(true)
        }
    }

    private var cardGrid: some View {
        // Each cell is identified by its card's canonical id, never by its position (`GAP-031`).
        ForEach(Array(state.items.enumerated()), id: \.element.gridIdentity) { index, card in
            CharacterCardCell(
                card: card,
                loader: loader,
                identifierPrefix: "discovery.card",
                action: { onOpenDetail(card) }
            )
            .onAppear { requestNextPageIfNearEnd(index) }
        }
    }

    /// The bottom glass banner of `UI_SPEC.md` §8: the content stays visible while the stale state or
    /// the failed load is stated, and its Retry recovers either (`ERROR_FLOW.md` §4, §9, `DEC-124`).
    private func noticeBanner(_ notice: DiscoveryNotice) -> some View {
        HStack(spacing: MultiverseDimensions.spaceM) {
            Text(notice.message)
                .font(MultiverseType.subheadline)
                .foregroundStyle(MultiverseColors.onSecondaryContainer)
                .frame(maxWidth: .infinity, alignment: .leading)
            GlassTextButton(label: notice.actionLabel) {
                onIntent(CharacterListIntentRetry.shared)
            }
        }
        .padding(.horizontal, MultiverseDimensions.spaceL)
        .padding(.vertical, MultiverseDimensions.spaceS)
        .glassSurface(.rounded(MultiverseDimensions.spaceL), tint: MultiverseColors.secondaryContainer)
        .padding(MultiverseDimensions.spaceM)
    }

    /// The paging indicator as the last grid item (`UI_SPEC.md` §8): a `ProgressView` in a glass
    /// capsule.
    private var pagingIndicator: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .padding(MultiverseDimensions.spaceM)
            .glassSurface(.capsule)
            .frame(maxWidth: .infinity)
            .accessibilityLabel(Text(copy(.splashLoading)))
    }

    // MARK: - Helpers

    private func copy(_ key: String) -> String {
        LocalizedCopy.shared.text(for: key)
    }

    private func copy(_ key: CopyKey) -> String {
        LocalizedCopy.shared.text(for: key)
    }
}

/// The fixed geometry of the Discovery surface (`UI_SPEC.md` §4.1, §8).
enum DiscoveryLayout {
    /// The skeleton count of `UI_SPEC.md` §8, "Initial loading".
    static let skeletonCount = 6

    /// The count line's fade when the count leaves or returns (`UI_SPEC.md` §6.2), in seconds.
    static let countLineFade: Double = 0.15

    /// How many cards before the end a card's appearance requests the next page (`API_SPECS.md` §8): the
    /// distance Android's grid uses, so both platforms prefetch at the same point.
    static let prefetchDistance = 2

    /// One identity per skeleton, disjoint from every card's `CharacterPresentation.gridIdentity`.
    static let skeletonIdentities = (0..<skeletonCount).map { "skeleton-\($0)" }

    /// The portal logo's size on the empty search (`UI_SPEC.md` §8): the empty-state well's.
    static let emptyMarkSize: CGFloat = MultiverseDimensions.emptyStateWell

    /// The height the empty and error surfaces keep inside the scrolling content, so they sit in the
    /// screen's middle rather than collapsing against the filters.
    static let fullSurfaceMinHeight: CGFloat = 420
}

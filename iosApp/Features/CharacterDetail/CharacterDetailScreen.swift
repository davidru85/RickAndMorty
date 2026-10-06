import MultiverseExplorer
import SwiftUI

/// The iOS Character detail screen (`UI_SPEC.md` §6.3, §8, `IC-019`, `TASK-055`).
///
/// It renders the shared `CharacterDetailUiState` and nothing it derives itself. The header — the
/// **list-provided** card the navigation hand-off carries in — renders in the first frame, so the
/// hero, the name, the status capsule and the subtitle are present before the detail response
/// arrives (`AC-REQ-FUNC-002-1`). A failure never clears that header: the inline error appears in
/// place of the info rows while the known fields stay (`AC-REQ-FUNC-002-3`, `ERROR_FLOW.md` §7); a
/// failure with no header renders the full-surface error instead (`DEC-131`).
///
/// The info rows are exactly the rows the state carries, in the state's fixed order: an unknown
/// origin or location reads "Unknown" (`AC-REQ-FUNC-002-2`, `DEC-131`), and the `First seen in` row
/// is **absent** rather than empty when the episode enrichment was not requested
/// (`REQ-FUNC-023`, `AC-REQ-FUNC-023-2`). [episodeCount]
/// comes from `episodeIds.size`, so the Episodes tile renders with or without the enrichment.
///
/// The layout is Figma `26:452`'s (`UI_SPEC.md` §5.3, §6.3): a full-screen blurred copy of the portrait
/// under a 38 % Space Black dim and the Detail's two glows is the glass backdrop; the sharp 402 × 520
/// hero dissolves into it from 55 % of its height; and the title block, the frosted panel and the
/// episode line are one stack that starts over the hero's lower part — 162 pt above its bottom, where
/// Figma places the title — so rows arriving later grow it downwards without moving the title, and a
/// larger text size scrolls rather than clips. The controls stay fixed at the top.
///
/// The screen owns only the platform concerns: the layout, the SF Symbols, the glass controls and the
/// accessibility shape. Every interaction leaves as a `CharacterDetailIntent`, and the screen never
/// reaches a use case or a repository (`IC-019`, `CONTRACTS.md` R2).
struct CharacterDetailScreen: View {
    let state: CharacterDetailUiState
    let onIntent: (any CharacterDetailIntent) -> Void
    let onBack: () -> Void
    let onShare: () -> Void

    /// The one image seam (`TASK-058`, `UI_SPEC.md` §5.1); `nil` uses the app's own pipeline.
    private let loader: (any PortraitImageLoading)?

    init(
        state: CharacterDetailUiState,
        loader: (any PortraitImageLoading)? = nil,
        onIntent: @escaping (any CharacterDetailIntent) -> Void,
        onBack: @escaping () -> Void,
        onShare: @escaping () -> Void
    ) {
        self.state = state
        self.loader = loader
        self.onIntent = onIntent
        self.onBack = onBack
        self.onShare = onShare
    }

    /// The hero's size (`UI_SPEC.md` §6.3): 402 × 520.
    private static let heroAspectRatio: CGFloat = 402 / 520

    /// How far above the hero's bottom the stack starts (Figma `31:396`: the title block at y 358 of
    /// the 520 pt hero).
    private static let titleRise: CGFloat = 162

    /// The editorial display scales with Dynamic Type through `@ScaledMetric` (`UI_SPEC.md` §3.4,
    /// §9), so the fixed token size is not a fixed rendered size.
    @ScaledMetric(relativeTo: .largeTitle) private var editorialDisplaySize = MultiverseType.editorialDisplaySize

    /// SF Pro Expanded Heavy at the scaled size (`UI_SPEC.md` §3.4).
    private var editorialDisplay: Font {
        MultiverseType.editorialDisplay(size: editorialDisplaySize)
    }

    /// Reduce Motion stills the favourite's bounce (`UI_SPEC.md` §7).
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        if let failure = (state.loadState as? LoadStateError)?.failure, state.header == nil {
            // No header means nothing known to keep, so the surface is the full error state rather
            // than an empty hero (`AC-REQ-UX-009-1`, `DEC-131`).
            fullSurfaceError(failure: failure)
        } else {
            ZStack(alignment: .top) {
                GeometryReader { proxy in
                    ScrollView {
                        ZStack(alignment: .top) {
                            hero
                            stack
                                .padding(.top, proxy.size.width / Self.heroAspectRatio - Self.titleRise)
                        }
                    }
                    .scrollIndicators(.hidden)
                }
                // The hero and the stack start at the top of the screen, under the status bar.
                .ignoresSafeArea(edges: .top)
                heroControls
            }
            .background { backdrop }
        }
    }

    /// The stack of `UI_SPEC.md` §6.3: the title block, the frosted panel and the episode line.
    private var stack: some View {
        VStack(alignment: .leading, spacing: MultiverseDimensions.spaceM) {
            titleBlock
            panel
            episodeCountLine
        }
        .padding(.bottom, MultiverseDimensions.spaceS)
    }

    // MARK: - Backdrop

    /// The glass backdrop (`UI_SPEC.md` §5.3, Figma `26:454`…`26:458`): the portrait again, full
    /// screen and blurred, under the Space Black dim and the Detail's two glows.
    private var backdrop: some View {
        DetailBackdrop(imageUrl: state.header?.imageUrl ?? "", loader: loader)
    }

    // MARK: - Failure

    /// The full-surface error (`UI_SPEC.md` §8, iOS column: "`ContentUnavailableView` + Retry glass
    /// button"): the shared title, the failure's own message and its `IC-017` recovery — Back for a
    /// not-found detail, Retry otherwise (`ERROR_FLOW.md` §4, §10) — under the back control the hero
    /// would otherwise carry.
    private func fullSurfaceError(failure: any ApiFailure) -> some View {
        let formatters = DefaultPresentationFormatters.shared
        let recovery = formatters.recovery(failure: failure)
        return ContentUnavailableView {
            Label(copy(CharacterPresentation.key(formatters.failureTitle())), systemImage: "network.slash")
        } description: {
            Text(CharacterPresentation.message(formatters.failureMessage(failure: failure)))
        } actions: {
            GlassTextButton(label: copy(CharacterPresentation.key(recovery.actionKey))) {
                perform(recovery)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay(alignment: .topLeading) {
            GlassIconButton(
                systemImage: "chevron.left",
                style: .glass,
                accessibilityLabel: copy(.actionBack),
                action: onBack
            )
            .padding(MultiverseDimensions.spaceL)
        }
    }

    /// Runs [recovery]: Retry is an intent to the state holder, Back is the caller's navigation.
    private func perform(_ recovery: Recovery) {
        if recovery == Recovery.back {
            onBack()
        } else {
            onIntent(CharacterDetailIntentRetry.shared)
        }
    }

    // MARK: - Hero

    /// The full-bleed portrait with the tonal controls over it (`UI_SPEC.md` §6.3). The portrait is
    /// the caller's own renderer (`TASK-058`), so this screen names no image API and holds no bytes.
    ///
    /// The hero is a **containing** element labelled with the character's name rather than a merged
    /// one: the portrait stands alone here rather than inside a card, so it carries the name, while
    /// the back, share and favourite controls stay individually reachable elements
    /// (`UI_SPEC.md` §9).
    private var hero: some View {
        // The frame is sized first — the full width at the hero's ratio, which in the scroll view's
        // unbounded height is what fixes the 402 × 520 box — and the portrait then fills and is clipped
        // to it. A `.fill` aspect ratio on an unbounded frame covered the whole screen instead.
        Color.clear
            .aspectRatio(Self.heroAspectRatio, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .overlay {
                DetailHeroPortrait(imageUrl: state.header?.imageUrl ?? "", loader: loader)
            }
            .clipped()
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Text(state.header?.name ?? copy(.navCharacters)))
    }

    /// The back, share and favourite controls (`UI_SPEC.md` §4.1, §6.3). The favourite is the
    /// integrated glass button: `Glass` + `heart` unmarked, `Prominent` + `heart.fill` marked, and it
    /// exposes its toggled state (`UI_SPEC.md` §9).
    private var heroControls: some View {
        HStack {
            GlassIconButton(
                systemImage: "chevron.left",
                style: .glass,
                accessibilityLabel: copy(.actionBack),
                action: onBack
            )
            Spacer()
            HStack(spacing: MultiverseDimensions.spaceS) {
                GlassIconButton(
                    systemImage: "square.and.arrow.up",
                    style: .glass,
                    accessibilityLabel: copy(.actionShare),
                    action: onShare
                )
                favouriteButton
            }
        }
        .padding(.horizontal, MultiverseDimensions.spaceL)
    }

    /// The favourite toggle (`UI_SPEC.md` §6.3, §7 "Favorite"): the heart bounces and the device gives
    /// the success haptic when the state changes; Reduce Motion keeps the haptic and stills the bounce.
    private var favouriteButton: some View {
        GlassIconButton(
            systemImage: state.isFavorite ? "heart.fill" : "heart",
            style: state.isFavorite ? .prominent : .glass,
            accessibilityLabel: copy(.detailActionFavorite),
            action: { onIntent(CharacterDetailIntentToggleFavorite.shared) }
        )
        .symbolEffect(.bounce, options: reduceMotion ? .nonRepeating.speed(0) : .nonRepeating, value: state.isFavorite)
        .sensoryFeedback(.success, trigger: state.isFavorite)
        .accessibilityAddTraits(state.isFavorite ? [.isSelected] : [])
    }

    // MARK: - Title

    /// The capsule, the name and the subtitle (`UI_SPEC.md` §6.3).
    @ViewBuilder
    private var titleBlock: some View {
        if let header = state.header {
            VStack(alignment: .leading, spacing: MultiverseDimensions.spaceS) {
                GlassStatusCapsule(
                    tone: CharacterPresentation.tone(header.status),
                    label: copy(CharacterPresentation.key(header.statusLabel))
                )
                Text(header.name)
                    .font(editorialDisplay)
                    .foregroundStyle(MultiverseLabelColors.primary)
                    .lineLimit(2)
                    .shadow(color: MultiverseBrandColors.spaceBlack, radius: MultiverseDimensions.spaceS)
                if let subtitle {
                    Text(subtitle)
                        .font(MultiverseType.title3Semibold)
                        .foregroundStyle(MultiverseLabelColors.secondary)
                        .lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, MultiverseDimensions.spaceL)
        }
    }

    /// `Species · Gender`, joined by the shared helper from parts the state already carries
    /// (`UI_SPEC.md` §6.3, `DEC-131`).
    private var subtitle: String? {
        guard let header = state.header else { return nil }
        return CharacterPresentation.subtitle(
            species: CharacterPresentation.text(header.species),
            gender: state.gender
        )
    }

    // MARK: - Panel

    /// The frosted panel (`UI_SPEC.md` §4.2, §6.3): the stats row, the divider and the info rows — or,
    /// on a failure, the inline retry in their place (`ERROR_FLOW.md` §4, §8).
    private var panel: some View {
        GlassPanel {
            VStack(alignment: .leading, spacing: MultiverseDimensions.spaceL) {
                stats
                Rectangle()
                    .fill(MultiverseGlassColors.strokeHighlight)
                    .frame(height: MultiverseDimensions.rimWidth)
                    .accessibilityHidden(true)
                info
            }
        }
        .padding(.horizontal, MultiverseDimensions.spaceL)
    }

    /// The informative "Appears in N episodes" line below the panel (`UI_SPEC.md` §6.3, `DEC-132`): a
    /// plain label, not a control, aligned with the panel content. It needs the count, so it is absent
    /// until the detail answers rather than showing a placeholder number.
    @ViewBuilder
    private var episodeCountLine: some View {
        if let count = state.episodeCount {
            Label(
                CharacterPresentation.episodeCountLine(Int(count.int32Value)),
                systemImage: "play.rectangle.on.rectangle.fill"
            )
            .font(MultiverseType.subheadline)
            .foregroundStyle(MultiverseLabelColors.secondary)
            .padding(.horizontal, MultiverseDimensions.spaceL + MultiverseDimensions.glassPanelPadding)
        }
    }

    /// The stats row (`UI_SPEC.md` §6.3): Episodes · Dimension · Species, from the state alone.
    @ViewBuilder
    private var stats: some View {
        if let header = state.header {
            DetailStatsRow(
                episodeCount: state.episodeCount.map { Int($0.int32Value) },
                dimension: state.dimension,
                species: CharacterPresentation.text(header.species)
            )
        }
    }

    /// The info rows and the inline error (`UI_SPEC.md` §6.3, §8).
    ///
    /// On a failure the list is replaced by the inline error — never by a full-surface error, because
    /// the header above it is still the list's data (`AC-REQ-FUNC-002-3`) — with the `IC-017` message
    /// and recovery: `detail_error_inline` and Retry, or the not-found message and Back (`DEC-131`).
    /// Otherwise the rows are exactly the ones the state carries, each value resolved by the shared
    /// boundary mapping.
    @ViewBuilder
    private var info: some View {
        if let failure = (state.loadState as? LoadStateError)?.failure {
            inlineError(failure: failure)
        } else if !state.info.isEmpty {
            VStack(alignment: .leading, spacing: MultiverseDimensions.spaceS) {
                ForEach(state.info, id: \.kind) { row in
                    GlassInfoRow(
                        symbol: CharacterPresentation.infoSymbol(for: row.kind),
                        label: copy(CharacterPresentation.key(row.copyKey)),
                        value: CharacterPresentation.text(row.value)
                    )
                }
            }
        }
    }

    /// The inline error of `ERROR_FLOW.md` §4: the message plus the failure's one recovery.
    private func inlineError(failure: any ApiFailure) -> some View {
        let formatters = DefaultPresentationFormatters.shared
        let recovery = formatters.recovery(failure: failure)
        return VStack(alignment: .leading, spacing: MultiverseDimensions.spaceS) {
            Text(CharacterPresentation.message(formatters.inlineFailureMessage(failure: failure)))
                .font(MultiverseType.subheadline)
                .foregroundStyle(MultiverseColors.onErrorContainer)
            GlassTextButton(label: copy(CharacterPresentation.key(recovery.actionKey))) {
                perform(recovery)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(MultiverseDimensions.spaceM)
        .background(content: {
            RoundedRectangle(cornerRadius: MultiverseDimensions.cornerLarge, style: .continuous)
                .fill(MultiverseColors.errorContainer)
        })
    }

    // MARK: - Helpers

    private func copy(_ key: String) -> String {
        LocalizedCopy.shared.text(for: key)
    }

    private func copy(_ key: CopyKey) -> String {
        LocalizedCopy.shared.text(for: key)
    }
}

#Preview("Detail — loading") { CharacterDetailScreen.preview(PreviewFixtures.detailLoading) }

#Preview("Detail — content") { CharacterDetailScreen.preview(PreviewFixtures.detailContent) }

#Preview("Detail — inline error") { CharacterDetailScreen.preview(PreviewFixtures.detailInlineError) }

#Preview("Detail — error") { CharacterDetailScreen.preview(PreviewFixtures.detailError) }

#Preview("Detail — largest Dynamic Type") {
    CharacterDetailScreen.preview(PreviewFixtures.detailContent, .largestDynamicType)
}

#Preview("Detail — Reduce Transparency") {
    CharacterDetailScreen.preview(PreviewFixtures.detailContent, .reduceTransparency)
}

#Preview("Detail — material fallback") {
    CharacterDetailScreen.preview(PreviewFixtures.detailContent, .materialFallback)
}

import MultiverseExplorer
import SwiftUI

/// The iOS Character detail screen (`UI_SPEC.md` §6.3, §8, `IC-019`, `TASK-055`).
///
/// It renders the shared `CharacterDetailUiState` and nothing it derives itself. The header — the
/// **list-provided** card the navigation hand-off carries in — renders in the first frame, so the
/// hero, the name, the status capsule and the subtitle are present before the detail response
/// arrives (`AC-REQ-FUNC-002-1`). A failure never clears that header: the inline retry appears in
/// place of the info rows while the known fields stay (`AC-REQ-FUNC-002-3`, `ERROR_FLOW.md` §7).
///
/// The info rows are exactly the rows the state carries, in the state's fixed order, so a row whose
/// value is absent is **absent** rather than empty — the `First seen in` row does not exist when
/// the episode enrichment was not requested (`REQ-FUNC-023`, `AC-REQ-FUNC-023-2`). [episodeCount]
/// comes from `episodeIds.size`, so the Episodes tile renders with or without the enrichment.
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

    /// The editorial display scales with Dynamic Type through `@ScaledMetric` (`UI_SPEC.md` §3.4,
    /// §9), so the fixed token size is not a fixed rendered size.
    @ScaledMetric(relativeTo: .largeTitle) private var editorialDisplaySize = MultiverseType.editorialDisplaySize

    private var editorialDisplay: Font {
        Font.system(size: editorialDisplaySize, weight: MultiverseType.editorialDisplayWeight)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: MultiverseDimensions.spaceL) {
                hero
                titleBlock
                panel
            }
            .padding(.bottom, MultiverseDimensions.spaceL)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
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
                CharacterPortrait(url: state.header?.imageUrl ?? "", loader: loader)
            }
            .clipped()
            .overlay(alignment: .top) { heroControls }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Text(state.header?.name ?? copy("nav_characters")))
    }

    /// The back, share and favourite controls (`UI_SPEC.md` §4.1, §6.3). The favourite is the
    /// integrated glass button: `Glass` + `heart` unmarked, `Prominent` + `heart.fill` marked, and it
    /// exposes its toggled state (`UI_SPEC.md` §9).
    private var heroControls: some View {
        HStack {
            GlassIconButton(
                systemImage: "chevron.left",
                style: .glass,
                accessibilityLabel: copy("action_back"),
                action: onBack
            )
            Spacer()
            HStack(spacing: MultiverseDimensions.spaceS) {
                GlassIconButton(
                    systemImage: "square.and.arrow.up",
                    style: .glass,
                    accessibilityLabel: copy("action_share"),
                    action: onShare
                )
                favouriteButton
            }
        }
        .padding(MultiverseDimensions.spaceL)
    }

    private var favouriteButton: some View {
        GlassIconButton(
            systemImage: state.isFavorite ? "heart.fill" : "heart",
            style: state.isFavorite ? .prominent : .glass,
            accessibilityLabel: copy("detail_action_favorite"),
            action: { onIntent(CharacterDetailIntentToggleFavorite.shared) }
        )
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
                        .font(MultiverseType.subheadline)
                        .foregroundStyle(MultiverseLabelColors.secondary)
                        .lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, MultiverseDimensions.spaceL)
        }
    }

    /// `Species · Origin`, joined by the shared helper from parts the state already carries
    /// (`UI_SPEC.md` §6.3).
    private var subtitle: String? {
        guard let header = state.header else { return nil }
        return CharacterPresentation.subtitle(
            species: CharacterPresentation.text(header.species),
            info: state.info
        )
    }

    // MARK: - Panel

    /// The frosted panel (`UI_SPEC.md` §4.2, §6.3): the stats row, the divider and the info rows — or,
    /// on a failure, the inline retry in their place (`ERROR_FLOW.md` §4, §8).
    private var panel: some View {
        GlassPanel {
            VStack(alignment: .leading, spacing: MultiverseDimensions.spaceM) {
                stats
                Divider()
                info
            }
        }
        .padding(.horizontal, MultiverseDimensions.spaceL)
    }

    /// The three connected tiles (`UI_SPEC.md` §6.3): Episodes count · Dimension · Species. A `nil`
    /// dimension hides its tile rather than rendering a placeholder (`IC-019`).
    @ViewBuilder
    private var stats: some View {
        if let header = state.header {
            HStack(alignment: .top, spacing: MultiverseDimensions.spaceS) {
                if let count = state.episodeCount {
                    statTile(
                        value: String(count.int32Value),
                        label: copy("detail_stat_episodes"),
                        container: MultiverseColors.primaryContainer,
                        content: MultiverseColors.onPrimaryContainer
                    )
                }
                if let dimension = state.dimension {
                    statTile(
                        value: dimension,
                        label: copy("detail_stat_dimension"),
                        container: MultiverseColors.tertiaryContainer,
                        content: MultiverseColors.onTertiaryContainer
                    )
                }
                statTile(
                    value: CharacterPresentation.text(header.species),
                    label: copy("detail_stat_species"),
                    container: MultiverseColors.secondaryFixedDim,
                    content: MultiverseColors.onSecondaryFixed
                )
            }
        }
    }

    /// One stat tile of `UI_SPEC.md` §6.3.
    private func statTile(value: String, label: String, container: Color, content: Color) -> some View {
        VStack(alignment: .leading, spacing: MultiverseDimensions.spaceXs) {
            Text(value)
                .font(MultiverseType.subheadlineEmphasized)
                .foregroundStyle(content)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text(label)
                .font(MultiverseType.caption2Emphasized)
                .foregroundStyle(content)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(MultiverseDimensions.spaceM)
        .background(content: {
            RoundedRectangle(cornerRadius: MultiverseDimensions.cornerLarge, style: .continuous)
                .fill(container)
        })
        .accessibilityElement(children: .combine)
    }

    /// The info rows and the inline error (`UI_SPEC.md` §6.3, §8).
    ///
    /// On a failure the list is replaced by `detail_error_inline` and its Retry — never by a
    /// full-surface error, because the header above it is still the list's data
    /// (`AC-REQ-FUNC-002-3`). Otherwise the rows are exactly the ones the state carries, so an absent
    /// value is an absent row.
    @ViewBuilder
    private var info: some View {
        if state.loadState is LoadStateError {
            inlineError
        } else if !state.info.isEmpty {
            VStack(alignment: .leading, spacing: MultiverseDimensions.spaceS) {
                ForEach(state.info, id: \.kind) { row in
                    GlassInfoRow(
                        symbol: CharacterPresentation.infoSymbol(for: row.kind),
                        label: copy(CharacterPresentation.key(row.copyKey)),
                        value: row.value
                    )
                }
            }
        }
    }

    /// The inline error of `ERROR_FLOW.md` §4: the message plus the shared retry affordance.
    private var inlineError: some View {
        VStack(alignment: .leading, spacing: MultiverseDimensions.spaceS) {
            Text(copy("detail_error_inline"))
                .font(MultiverseType.subheadline)
                .foregroundStyle(MultiverseColors.onErrorContainer)
            GlassTextButton(label: copy("action_retry")) {
                onIntent(CharacterDetailIntentRetry.shared)
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
}

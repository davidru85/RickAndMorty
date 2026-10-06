import SwiftUI

// The Detail's own parts (`UI_SPEC.md` §5.3, §6.3, Figma `26:452`), kept beside the screen so the
// screen file stays within the repository's length limits. Each takes primitives and the image seam
// only, so none reaches a state holder or a use case (`CONTRACTS.md` R2).

/// The glass backdrop (`UI_SPEC.md` §5.3, Figma `26:454`…`26:458`): the portrait again, full screen and
/// blurred, under the 38 % Space Black dim and the Detail's two glows.
struct DetailBackdrop: View {
    let imageUrl: String
    let loader: (any PortraitImageLoading)?

    /// Figma's 64 pt layer blur, which is a Gaussian of half that radius.
    private static let blur: CGFloat = 32

    /// The Space Black dim (`UI_SPEC.md` §5.3).
    private static let dim: Double = 0.38

    var body: some View {
        CharacterPortrait(url: imageUrl, loader: loader)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            .blur(radius: Self.blur, opaque: true)
            .overlay { MultiverseBrandColors.spaceBlack.opacity(Self.dim) }
            .overlay { CosmicCanvas(.detail, base: false) }
            .ignoresSafeArea()
            .accessibilityHidden(true)
    }
}

/// The sharp hero portrait (`UI_SPEC.md` §5.3): opaque to 55 % of its height, then dissolving into the
/// backdrop through an alpha mask and a progressive blur.
struct DetailHeroPortrait: View {
    let imageUrl: String
    let loader: (any PortraitImageLoading)?

    /// Where the hero starts dissolving into the backdrop: 55 % of its height.
    private static let dissolveStart: CGFloat = 0.55

    var body: some View {
        ZStack {
            CharacterPortrait(url: imageUrl, loader: loader)
                .mask { fade }
            // The progressive blur: a blurred copy that takes over below 55 % and fades out too.
            CharacterPortrait(url: imageUrl, loader: loader)
                .blur(radius: MultiverseDimensions.spaceL)
                .mask { dissolveBand }
        }
    }

    /// Opaque from the top to the dissolve, then fading to clear at the bottom.
    private var fade: some View {
        var stops: [Gradient.Stop] = []
        stops.append(.init(color: .black, location: 0))
        stops.append(.init(color: .black, location: Self.dissolveStart))
        stops.append(.init(color: .clear, location: 1))
        return LinearGradient(stops: stops, startPoint: .top, endPoint: .bottom)
    }

    /// Clear above the dissolve, strongest between it and the bottom, clear again at the bottom edge.
    private var dissolveBand: some View {
        var stops: [Gradient.Stop] = []
        stops.append(.init(color: .clear, location: Self.dissolveStart - 0.05))
        stops.append(.init(color: .black, location: 0.8))
        stops.append(.init(color: .clear, location: 1))
        return LinearGradient(stops: stops, startPoint: .top, endPoint: .bottom)
    }
}

/// The stats row of the frosted panel (`UI_SPEC.md` §4.2, §6.3, Figma `31:403`): Episodes count ·
/// Dimension · Species in equal columns split by 1 pt separators, each a Title 2 value over a Caption 1
/// label, the Episodes value in Portal Glow. A `nil` count or dimension drops its column rather than
/// rendering a placeholder (`IC-019`). At the accessibility sizes the three stack, each value on its
/// own lines, so none is truncated (`REQ-UX-006`).
struct DetailStatsRow: View {
    let episodeCount: Int?
    let dimension: String?
    let species: String

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var stacked: Bool { dynamicTypeSize.isAccessibilitySize }

    var body: some View {
        let layout =
            stacked
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: MultiverseDimensions.spaceM))
            : AnyLayout(HStackLayout(spacing: 0))
        layout {
            if let episodeCount {
                stat(
                    value: String(episodeCount),
                    label: copy(.detailStatEpisodes),
                    tint: MultiverseBrandColors.portalGlow
                )
                separator
            }
            if let dimension {
                stat(value: dimension, label: copy(.detailStatDimension), tint: MultiverseLabelColors.primary)
                separator
            }
            stat(value: species, label: copy(.detailStatSpecies), tint: MultiverseLabelColors.primary)
        }
    }

    /// One column of the row.
    private func stat(value: String, label: String, tint: Color) -> some View {
        VStack(alignment: stacked ? .leading : .center, spacing: 1) {
            Text(value)
                .font(MultiverseType.title2Bold)
                .foregroundStyle(tint)
                .lineLimit(stacked ? nil : 1)
                .minimumScaleFactor(stacked ? 1 : 0.7)
                .fixedSize(horizontal: false, vertical: stacked)
            Text(label)
                .font(MultiverseType.caption1)
                .foregroundStyle(MultiverseLabelColors.secondary)
                .lineLimit(stacked ? nil : 1)
                .fixedSize(horizontal: false, vertical: stacked)
        }
        .frame(maxWidth: .infinity, alignment: stacked ? .leading : .center)
        .accessibilityElement(children: .combine)
    }

    /// The 1 pt separator between two columns; stacked stats need none.
    @ViewBuilder
    private var separator: some View {
        if !stacked {
            Rectangle()
                .fill(MultiverseGlassColors.strokeHighlight)
                .frame(width: MultiverseDimensions.rimWidth, height: MultiverseDimensions.space2Xl)
                .accessibilityHidden(true)
        }
    }

    private func copy(_ key: String) -> String {
        LocalizedCopy.shared.text(for: key)
    }

    private func copy(_ key: CopyKey) -> String {
        LocalizedCopy.shared.text(for: key)
    }
}

#Preview("Detail stats row") {
    DetailStatsRow(episodeCount: 51, dimension: "C-137", species: "Human")
        .padding()
        .background(MultiverseBrandColors.spaceBlack)
        .previewVariant()
}

#Preview("Detail stats row — largest Dynamic Type") {
    DetailStatsRow(episodeCount: 51, dimension: "C-137", species: "Human")
        .padding()
        .background(MultiverseBrandColors.spaceBlack)
        .previewVariant(.largestDynamicType)
}

#Preview("Detail hero over its backdrop") {
    DetailHeroPortrait(imageUrl: PreviewFixtures.portraitUrl(id: "1"), loader: PreviewPortraitLoader.loaded)
        .background {
            DetailBackdrop(imageUrl: PreviewFixtures.portraitUrl(id: "1"), loader: PreviewPortraitLoader.loaded)
        }
        .previewVariant()
}

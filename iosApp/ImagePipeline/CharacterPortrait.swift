import SwiftUI

/// The SwiftUI portrait renderer (`UI_SPEC.md` §5.1, §5.3, `TASK-055`/`TASK-058`).
///
/// It draws the three states the specification fixes and nothing else:
///
/// - **Placeholder** — a `Glass/Fill` box with a 1 s shimmer until an image resolves (`§5.1`).
/// - **Image** — the decoded portrait, `.scaledToFill()`, cross-faded in over 200 ms (`§5.1`).
/// - **Error** — the same container with the branded portal mark centred at 40 % opacity; never a
///   broken-image glyph (`§5.1`).
///
/// The portrait resolves through the one seam, [PortraitImageLoading] (`TASK-058`): `nil` uses the
/// app's own pipeline, and a test injects an in-memory loader so no case touches the network. The
/// portrait is decorative inside a card (`UI_SPEC.md` §9), so it never contributes an accessibility
/// label of its own; the containing card is the one merged element.
public struct CharacterPortrait: View {
    private let url: String
    private let loader: (any PortraitImageLoading)?

    public init(url: String, loader: (any PortraitImageLoading)? = nil) {
        self.url = url
        self.loader = loader
    }

    @State private var image: Image?
    @State private var failed = false

    /// The loader this render resolves through; the injected one wins for a test, otherwise the app
    /// resolves its single pipeline.
    private var resolvedLoader: any PortraitImageLoading {
        loader ?? PortraitImagePipeline.shared
    }

    public var body: some View {
        content
            .task(id: url) { await load() }
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private var content: some View {
        if let image {
            image
                .resizable()
                .scaledToFill()
                .transition(.opacity)
        } else {
            // The placeholder and the error share the container; only the mark distinguishes them
            // (`UI_SPEC.md` §5.1), so a failed load never collapses the card's geometry.
            PortraitPlaceholder(showsPortalMark: failed, animated: !failed)
        }
    }

    private func load() async {
        guard !url.isEmpty else {
            image = nil
            failed = true
            return
        }
        // The synchronous memory hit paints without a suspension; only a miss suspends.
        if let cached = resolvedLoader.cachedImage(for: url) {
            image = cached
            return
        }
        let loaded = await resolvedLoader.image(for: url)
        withAnimation(.easeInOut(duration: Self.crossfadeSeconds)) {
            image = loaded
            failed = loaded == nil
        }
    }

    /// The cross-fade duration of `UI_SPEC.md` §5.1.
    private static let crossfadeSeconds: Double = 0.2

    /// The shimmer period of `UI_SPEC.md` §5.1.
    private static let shimmerSeconds: Double = 1.0
}

/// The `Glass/Fill` container a portrait shows before its image resolves, or the branded portal mark
/// at 40 % opacity when the load failed (`UI_SPEC.md` §5.1).
///
/// It lives here rather than in the design system because it is the portrait pipeline's own state,
/// not one of the catalogued components (`UI_SPEC.md` §1.2); it takes `Multiverse` tokens only, so it
/// still names no domain, presentation or route type.
private struct PortraitPlaceholder: View {
    let showsPortalMark: Bool
    let animated: Bool

    /// The shimmer drives an opacity pulse; with Reduce Motion on it is suppressed
    /// (`UI_SPEC.md` §7: parallax and drift are disabled under Reduce Motion).
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false

    var body: some View {
        ZStack {
            MultiverseColors.surfaceContainerHigh
            MultiverseGlassColors.fill
                .opacity(animated && !reduceMotion ? (pulse ? 0.35 : 1.0) : 1.0)
            if showsPortalMark {
                Image(systemName: "circle.circle")
                    .font(.system(size: MultiverseType.emptyStateSymbolSize, weight: .regular))
                    .foregroundStyle(MultiverseBrandColors.portalGlow.opacity(0.4))
            }
        }
        .animation(
            animated && !reduceMotion
                ? .easeInOut(duration: Self.shimmerSeconds).repeatForever(autoreverses: true)
                : nil,
            value: pulse
        )
        .onAppear { pulse = true }
    }

    private static let shimmerSeconds: Double = 1.0
}

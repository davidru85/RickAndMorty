import SwiftUI
import UIKit

/// `iOS/Glass tab item` (`UI_SPEC.md` §1.2, §4.2) — the definition of one tab: its identity, its SF
/// Symbol and its label, in the order the spec fixes.
///
/// In code the **system** tab bar draws each tab (that is what §4.2 prescribes), so this type
/// carries the pair the appearance proxy cannot: the symbol and the label. The paint of the normal
/// and selected states — unselected always white, selected Portal Glow — is
/// `GlassTabBarAppearance`, the code counterpart of the Figma component.
public struct GlassTabBarItem: Identifiable, Equatable, Sendable {
    /// The four destinations' SF Symbols, in `UI_SPEC.md` §4.2 order. SF Symbol names are not
    /// user-visible copy, so they may live in code; the labels do not and come from the caller.
    public enum Symbol: String, Sendable {
        case characters = "person.2.fill"
        case episodes = "play.tv.fill"
        case favorites = "heart.fill"
        case settings = "gearshape.fill"
    }

    public let id: String
    public let symbol: String
    public let label: String

    public init(id: String, symbol: String, label: String) {
        self.id = id
        self.symbol = symbol
        self.label = label
    }

    public init(id: String, symbol: Symbol, label: String) {
        self.init(id: id, symbol: symbol.rawValue, label: label)
    }
}

/// The tab bar's appearance (`UI_SPEC.md` §4.2): the selected tab in Portal Glow and every unselected
/// tab always in `Label/Primary` white, never the system's dimmed secondary colour.
///
/// The system tab bar is not a SwiftUI view that can be restyled directly, so this is the one place
/// the `UITabBarAppearance` is built — a screen never repeats it (`UI_SPEC.md` §4.2's one-definition
/// rule for a tab). `UITabBarAppearance` and its layouts are main-actor isolated, so this type is
/// too. It is exposed separately from the view so it can be asserted directly in a unit test, where
/// a rendered `UITabBar` cannot be.
@MainActor
public enum GlassTabBarAppearance {
    /// The appearance `UI_SPEC.md` §4.2 specifies, for all three tab bar layouts.
    public static func appearance() -> UITabBarAppearance {
        let appearance = UITabBarAppearance()
        appearance.configureWithDefaultBackground()
        for layout in Self.layouts(of: appearance) {
            layout.normal.iconColor = UIColor(MultiverseLabelColors.primary)
            layout.normal.titleTextAttributes = [
                .foregroundColor: UIColor(MultiverseLabelColors.primary)
            ]
            layout.selected.iconColor = UIColor(MultiverseBrandColors.portalGlow)
            layout.selected.titleTextAttributes = [
                .foregroundColor: UIColor(MultiverseBrandColors.portalGlow)
            ]
        }
        return appearance
    }

    /// The three tab bar layouts, returned by a function rather than a multiline collection literal:
    /// the repository's formatter and linter disagree about the trailing comma in one (see the note
    /// in `MultiverseTokensParityTests`), so a multiline literal cannot pass both tools.
    private static func layouts(of appearance: UITabBarAppearance) -> [UITabBarItemAppearance] {
        var layouts: [UITabBarItemAppearance] = []
        layouts.append(appearance.stackedLayoutAppearance)
        layouts.append(appearance.inlineLayoutAppearance)
        layouts.append(appearance.compactInlineLayoutAppearance)
        return layouts
    }

    /// Installs the appearance on the process-wide tab bar proxy. Idempotent, so the bar can call it
    /// from its initialiser before any tab bar view is built.
    public static func install() {
        let appearance = appearance()
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
}

/// The glass tab bar (`UI_SPEC.md` §4.2, §6.4): the four destinations' system Liquid Glass tab bar,
/// with the selected tab in Portal Glow and every unselected tab always white.
///
/// This component owns the tab bar's look so no screen repeats it: constructing it installs
/// `GlassTabBarAppearance`, and its `tint` matches the appearance so the two agree. The caller
/// supplies the destinations as content, so the design system names no route and no domain type, and
/// selection is a plain element id (`String`) a shell maps to the shared navigation vocabulary.
///
/// **Path selection.** Unlike the other components this one does not call `glassSurface`: §4.2
/// renders the bar through the system `TabView`, so the system selects the bar's material — Liquid
/// Glass on iOS 26, the pre-26 material on iOS 18 — and `GlassTabBarAppearance` only pins the two
/// paths' colours to the same tokens, so a caller cannot tell which rendered.
public struct GlassTabBar<Content: View>: View {
    @Binding private var selection: String
    private let items: [GlassTabBarItem]
    private let content: (GlassTabBarItem) -> Content

    /// Builds the bar over the destinations the shell resolved.
    ///
    /// - Parameters:
    ///   - selection: the selected tab's `id`.
    ///   - items: the tabs, in display order; `UI_SPEC.md` §4.2 fixes Characters, Episodes,
    ///     Favorites, Settings.
    ///   - content: the destination to render for each item.
    public init(
        selection: Binding<String>,
        items: [GlassTabBarItem],
        @ViewBuilder content: @escaping (GlassTabBarItem) -> Content
    ) {
        GlassTabBarAppearance.install()
        self._selection = selection
        self.items = items
        self.content = content
    }

    public var body: some View {
        TabView(selection: $selection) {
            ForEach(items) { item in
                Tab(item.label, systemImage: item.symbol, value: item.id) {
                    content(item)
                }
            }
        }
        .tint(MultiverseBrandColors.portalGlow)
    }
}

#Preview("Glass tab bar") { glassTabBarPreview(.standard) }

#Preview("Glass tab bar — largest Dynamic Type") { glassTabBarPreview(.largestDynamicType) }

/// The four destinations of `UI_SPEC.md` §4.2 with Characters selected, each tab over the cosmic canvas.
@MainActor
private func glassTabBarPreview(_ variant: PreviewVariant) -> some View {
    let copy = LocalizedCopy.shared
    var items: [GlassTabBarItem] = []
    items.append(GlassTabBarItem(id: "characters", symbol: .characters, label: copy.text(for: .navCharacters)))
    items.append(GlassTabBarItem(id: "episodes", symbol: .episodes, label: copy.text(for: .navEpisodes)))
    items.append(GlassTabBarItem(id: "favorites", symbol: .favorites, label: copy.text(for: .navFavorites)))
    items.append(GlassTabBarItem(id: "settings", symbol: .settings, label: copy.text(for: .navSettings)))
    return GlassTabBar(selection: .constant("characters"), items: items) { _ in
        Color.clear.cosmicCanvas()
    }
    .previewVariant(variant)
}

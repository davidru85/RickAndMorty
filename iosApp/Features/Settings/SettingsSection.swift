import SwiftUI

// The two §4.2 building blocks the Settings screen composes (`UI_SPEC.md` §4.2, "Settings section"
// and "Settings row"; `TASK-077`).
//
// They are `internal` rather than `private` so the screen file stays inside the repository's
// 400-line file limit, and they take primitives only — no domain, presentation or route type — so the
// feature's controls stay a leaf that a caller resolves values for (`DESIGN.md` §3.4). A caller that
// composed a fourth section would be a screen change rather than a usage, which is why neither type
// names the specification's sections itself.

/// One settings section: the header, the glass panel holding the rows, and the optional footer
/// (`UI_SPEC.md` §4.2: `Liquid Glass – Regular – Small`, continuous corner 26, header in Subheadline
/// Emphasized at `Label/Secondary`; the header and the footer 16 pt in from the panel's edge, Figma
/// `123:374`, `123:477`).
struct SettingsSection<Content: View>: View {
    let header: String
    let footer: String?
    @ViewBuilder let content: () -> Content

    init(header: String, footer: String? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.header = header
        self.footer = footer
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: MultiverseDimensions.spaceS) {
            Text(header)
                .font(MultiverseType.subheadlineEmphasized)
                .foregroundStyle(MultiverseLabelColors.secondary)
                .padding(.leading, MultiverseDimensions.spaceL)
            VStack(alignment: .leading, spacing: 0) {
                content()
            }
            .frame(maxWidth: MultiverseDimensions.glassContainerWidth)
            .glassSurface(.rounded(MultiverseDimensions.settingsPanelCorner))
            if let footer {
                Text(footer)
                    .font(MultiverseType.footnote)
                    .foregroundStyle(MultiverseLabelColors.secondary)
                    .padding(.horizontal, MultiverseDimensions.spaceL)
            }
        }
    }
}

/// One settings row (`UI_SPEC.md` §4.2, the iOS kit `Row`, Tall, 68 pt): a symbol in Portal Glow, a
/// Body title, an optional Subheadline subtitle in secondary, and the control as its trailing
/// content.
struct SettingsRow<Trailing: View>: View {
    let symbol: String?
    let title: String
    let subtitle: String?
    let isDestructive: Bool
    @ViewBuilder let trailing: () -> Trailing

    init(
        symbol: String? = nil,
        title: String,
        subtitle: String? = nil,
        isDestructive: Bool = false,
        @ViewBuilder trailing: @escaping () -> Trailing
    ) {
        self.symbol = symbol
        self.title = title
        self.subtitle = subtitle
        self.isDestructive = isDestructive
        self.trailing = trailing
    }

    /// A destructive row is the kit's 52 pt `Row - Button`; every other row is the 68 pt Tall row.
    private var minHeight: CGFloat {
        isDestructive ? MultiverseDimensions.settingsButtonRowHeight : MultiverseDimensions.settingsRowHeight
    }

    var body: some View {
        HStack(spacing: MultiverseDimensions.spaceM) {
            if let symbol {
                Image(systemName: symbol)
                    // A matched text style rather than a raw size, so the symbol scales with Dynamic
                    // Type (`GUIDELINES.md` §6.1, `UI_SPEC.md` §3.4).
                    .font(MultiverseType.body)
                    .foregroundStyle(MultiverseBrandColors.portalGlow)
            }
            VStack(alignment: .leading, spacing: MultiverseDimensions.spaceXs) {
                // A destructive row is the system's own red, as the iOS kit's `Row - Button` draws it
                // (`UI_SPEC.md` §4.2), not the M3 Error the Android screen uses.
                Text(title)
                    .font(MultiverseType.body)
                    .foregroundStyle(isDestructive ? Color.red : MultiverseLabelColors.primary)
                if let subtitle {
                    Text(subtitle)
                        .font(MultiverseType.subheadline)
                        .foregroundStyle(MultiverseLabelColors.secondary)
                }
            }
            Spacer(minLength: MultiverseDimensions.spaceM)
            trailing()
        }
        .padding(.horizontal, MultiverseDimensions.spaceL)
        .frame(minHeight: minHeight)
    }
}

/// A row that carries no trailing control — the data-source title row before its picker.
extension SettingsRow where Trailing == EmptyView {
    init(symbol: String? = nil, title: String, subtitle: String? = nil, isDestructive: Bool = false) {
        self.init(symbol: symbol, title: title, subtitle: subtitle, isDestructive: isDestructive) { EmptyView() }
    }
}

#Preview("Settings section and rows") {
    VStack(alignment: .leading, spacing: MultiverseDimensions.spaceXl) {
        SettingsSection(header: "Preferences", footer: "Remove every character you've saved.") {
            SettingsRow(symbol: "speaker.wave.2.fill", title: "Sounds", subtitle: "Play sound effects") {
                Toggle("", isOn: .constant(true)).labelsHidden()
            }
        }
        SettingsSection(header: "Favorites") {
            SettingsRow(title: "Delete favorites", isDestructive: true)
        }
    }
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .preferredColorScheme(.dark)
}

#Preview("Settings section — largest Dynamic Type") { settingsSectionPreview(.largestDynamicType) }

#Preview("Settings section — Reduce Transparency") { settingsSectionPreview(.reduceTransparency) }

#Preview("Settings section — material fallback") { settingsSectionPreview(.materialFallback) }

/// A section with a toggle row and its footer, and a destructive row, with the shipped copy.
@MainActor
private func settingsSectionPreview(_ variant: PreviewVariant) -> some View {
    let copy = LocalizedCopy.shared
    return VStack(alignment: .leading, spacing: MultiverseDimensions.spaceXl) {
        SettingsSection(
            header: copy.text(for: .settingsSectionPreferences),
            footer: copy.text(for: .settingsDeleteExplanation)
        ) {
            SettingsRow(
                symbol: "speaker.wave.2.fill",
                title: copy.text(for: .settingsSoundTitle),
                subtitle: copy.text(for: .settingsSoundBody)
            ) {
                Toggle(copy.text(for: .settingsSoundTitle), isOn: .constant(true)).labelsHidden()
            }
        }
        SettingsSection(header: copy.text(for: .navFavorites)) {
            SettingsRow(symbol: "trash.fill", title: copy.text(for: .settingsDeleteAction), isDestructive: true)
        }
    }
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .previewVariant(variant)
}

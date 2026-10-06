import SwiftUI

// The Liquid Glass controls of `UI_SPEC.md` §4.2 and §1.2.
//
// Every component here is **one** view with two visual paths, and the selection happens only in
// `GlassSurface.swift` (`GUIDELINES.md` §6.2): `glassSurface(_:tint:shadow:)` and `glassButton(_:)`
// draw the iOS 26+ `glassEffect` path on a current OS and the material path on the fallbacks — the
// pre-26 material on iOS 18 and the opaque one under Reduce Transparency. A component never calls
// `glassEffect` itself, so no call site duplicates the check and the paths cannot drift apart.
//
// Components take primitives only — no domain, presentation or route type — so the design system
// stays a leaf and a caller resolves its own values at the feature or shell boundary
// (`DESIGN.md` §3.4). User-visible text is passed in, never literal (`GUIDELINES.md` §5.7).

/// The glass **search field** (`UI_SPEC.md` §4.2, §1.2 `iOS/Glass search field`): a 370 × 48 capsule
/// with a leading `magnifyingglass` and the placeholder, with no trailing `mic.fill` while voice
/// search is deferred (`DEC-002`).
///
/// In a Discovery screen the search is applied through `.searchable(placement: .navigationBarDrawer)`
/// as §4.2 prescribes; this component is the standalone glass field the spec's Figma component
/// defines, so a screen that needs the field outside a navigation bar uses it.
public struct GlassSearchField: View {
    @Binding private var text: String
    private let placeholder: String

    public init(text: Binding<String>, placeholder: String) {
        self._text = text
        self.placeholder = placeholder
    }

    public var body: some View {
        HStack(spacing: MultiverseDimensions.spaceS) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(MultiverseLabelColors.secondary)
            TextField(
                "",
                text: $text,
                prompt: Text(placeholder).foregroundStyle(MultiverseLabelColors.secondary)
            )
            .font(MultiverseType.body)
            .foregroundStyle(MultiverseLabelColors.primary)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
        }
        .padding(.horizontal, MultiverseDimensions.spaceL)
        .frame(
            width: MultiverseDimensions.glassContainerWidth,
            height: MultiverseDimensions.glassSearchFieldHeight
        )
        .glassSurface(.capsule)
        .accessibilityLabel(Text(placeholder))
    }
}

/// The glass **segmented control** (`UI_SPEC.md` §4.2, §1.2 `iOS/Glass segmented control`): a 370 ×
/// 44 capsule with a 4 pt inset whose selected segment is a clear-glass capsule tinted Portal Green,
/// gliding with a spring.
///
/// The control renders inside a `GlassContainer` and the selection capsule is animated with
/// `matchedGeometryEffect`, as §4.2 prescribes. The options are caller-supplied because the spec's
/// four filter choices are copy; the control names none of them.
public struct GlassSegmentedControl: View {
    /// One segment: an id that identifies it and the label a caller already resolved.
    public struct Option: Identifiable, Equatable, Sendable {
        public let id: String
        public let label: String

        public init(id: String, label: String) {
            self.id = id
            self.label = label
        }
    }

    @Binding private var selection: String
    private let options: [Option]
    @Namespace private var namespace

    /// The reader's text size (`UI_SPEC.md` §9).
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    public init(selection: Binding<String>, options: [Option]) {
        self._selection = selection
        self.options = options
    }

    public var body: some View {
        if dynamicTypeSize.isAccessibilitySize {
            fittedBody
        } else {
            compactBody
        }
    }

    /// At the accessibility sizes the four labels cannot share the fixed capsule without truncating
    /// (`REQ-UX-006`), so each segment takes its label's width and the row scrolls horizontally.
    private var fittedBody: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: MultiverseDimensions.glassControlInset) {
                ForEach(options) { option in
                    segment(option, fitted: true)
                }
            }
            .padding(MultiverseDimensions.glassControlInset)
        }
        .glassSurface(.capsule)
        .accessibilityElement(children: .contain)
    }

    private var compactBody: some View {
        GlassContainer(spacing: MultiverseDimensions.glassControlInset) {
            HStack(spacing: MultiverseDimensions.glassControlInset) {
                ForEach(options) { option in
                    segment(option, fitted: false)
                }
            }
            .padding(MultiverseDimensions.glassControlInset)
            .frame(
                width: MultiverseDimensions.glassContainerWidth,
                height: MultiverseDimensions.glassControlHeight
            )
            .glassSurface(.capsule)
        }
        .accessibilityElement(children: .contain)
    }

    private func segment(_ option: Option, fitted: Bool) -> some View {
        let selected = option.id == selection
        return Button {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                selection = option.id
            }
        } label: {
            label(option, selected: selected, fitted: fitted)
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .background {
            // The selected segment is a clear-glass capsule tinted Portal Green at 30 % (`UI_SPEC.md`
            // §4.2), under a white label (Figma `29:419`).
            if selected {
                Color.clear
                    .glassSurface(.capsule, tint: MultiverseGlassColors.tintGreen)
                    .matchedGeometryEffect(id: SegmentSelection.id, in: namespace)
            }
        }
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    @ViewBuilder
    private func label(_ option: Option, selected: Bool, fitted: Bool) -> some View {
        let text = Text(option.label)
            .font(selected ? MultiverseType.subheadlineEmphasized : MultiverseType.subheadline)
            .foregroundStyle(selected ? MultiverseLabelColors.primary : MultiverseLabelColors.secondary)
        if fitted {
            text
                .fixedSize()
                .padding(.horizontal, MultiverseDimensions.spaceM)
                .padding(.vertical, MultiverseDimensions.spaceS)
        } else {
            text.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private enum SegmentSelection {
        static let id = "multiverse.segment.selection"
    }
}

/// The glass **panel** (`UI_SPEC.md` §4.2 "Frosted panel"): 370 wide, a continuous 34 corner and
/// 20 pt padding, holding a detail screen's stats row, divider and info rows.
public struct GlassPanel<Content: View>: View {
    private let content: () -> Content

    public init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    public var body: some View {
        content()
            .padding(MultiverseDimensions.glassPanelPadding)
            .frame(width: MultiverseDimensions.glassContainerWidth)
            .glassSurface(.rounded(MultiverseDimensions.glassPanel))
    }
}

#Preview("Glass search field") {
    GlassSearchField(text: .constant(""), placeholder: "Search characters")
        .padding()
        .background(MultiverseBrandColors.spaceBlack)
        .preferredColorScheme(.dark)
}

#Preview("Glass segmented control") {
    GlassSegmentedControl(
        selection: .constant("all"),
        options: segmentedPreviewOptions
    )
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .preferredColorScheme(.dark)
}

/// The spec's four filter options as a preview fixture. Built by appending because a multiline
/// collection literal cannot satisfy both the repository's formatter and its linter (see the note in
/// `MultiverseTokensParityTests`).
private var segmentedPreviewOptions: [GlassSegmentedControl.Option] {
    var options: [GlassSegmentedControl.Option] = []
    options.append(.init(id: "all", label: "All"))
    options.append(.init(id: "alive", label: "Alive"))
    options.append(.init(id: "dead", label: "Dead"))
    options.append(.init(id: "unknown", label: "Unknown"))
    return options
}

#Preview("Glass panel — material fallback") {
    GlassPanel {
        GlassInfoRow(symbol: "globe", label: "Origin", value: "Earth (C-137)")
    }
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .environment(\.multiverseGlassPath, .material)
    .preferredColorScheme(.dark)
}

#Preview("Glass controls — largest Dynamic Type") {
    VStack(spacing: MultiverseDimensions.spaceL) {
        GlassSearchField(text: .constant(""), placeholder: "Search characters")
        GlassPanel {
            GlassInfoRow(symbol: "globe", label: "Origin", value: "Earth (C-137)")
        }
    }
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .dynamicTypeSize(.accessibility5)
    .preferredColorScheme(.dark)
}

#Preview("Glass controls — Reduce Transparency") {
    VStack(spacing: MultiverseDimensions.spaceL) {
        GlassSearchField(text: .constant(""), placeholder: LocalizedCopy.shared.text(for: .searchCharacters))
        GlassSegmentedControl(selection: .constant("all"), options: segmentedPreviewOptions)
        GlassPanel {
            GlassInfoRow(
                symbol: "globe",
                label: LocalizedCopy.shared.text(for: .detailInfoOrigin),
                value: "Earth (C-137)"
            )
        }
    }
    .padding()
    .background(MultiverseBrandColors.spaceBlack)
    .previewVariant(.reduceTransparency)
}

import MultiverseExplorer
import SwiftUI

/// The iOS Settings screen (`UI_SPEC.md` §6.5, §4.2, `IC-023`, `TASK-077`).
///
/// It renders the shared `SettingsUiState` and nothing it derives itself: the Sounds flag, the
/// protocol in force, `canDeleteFavorites` and the confirmation lifecycle are all the shared
/// `SettingsStateHolder`'s (`CONTRACTS.md` §7 R1), so the Android and iOS screens cannot diverge.
/// The screen owns only the platform concerns — the glass panels and rows, the SF Symbols, the
/// controls and the accessibility shape — and never reaches a store, a repository or a use case
/// (`ERROR_FLOW.md` §3 invariant 4): every interaction leaves as a `SettingsIntent`.
///
/// The three settings render in the specification's order — **Preferences** (the Sounds toggle),
/// **Data** (the data-source picker) and **Favorites** (the destructive action) — each in its own
/// §4.2 section: header, `Liquid Glass` panel with the 26 pt continuous corner, and, for Favorites,
/// the explanation as the footer. Every string is a canonical key resolved through `LocalizedCopy`,
/// so no user-visible literal is in this source (`GUIDELINES.md` §5.7) and `TEST-UNIT-036` keeps the
/// two platforms byte-identical per locale. Every colour and measurement is a design-system token
/// (`GUIDELINES.md` §6.1).
///
/// The delete action is disabled while there are no favourites (`AC-REQ-FUNC-035-3`), opens the
/// confirmation only when there are (`AC-REQ-FUNC-035-1`), and the confirmation's "Delete" clears
/// every favourite (`AC-REQ-FUNC-035-2`) while "Cancel", a tap outside or the system dismissal
/// changes nothing — which is why any presentation-driven close dispatches the dismissal.
///
/// Like the Android screen, it is **stateless**: it takes an `IC-023` state plus an intent closure, so
/// it renders from a state alone in a test.
struct SettingsScreen: View {
    let state: SettingsUiState
    let onIntent: (SettingsIntent) -> Void

    init(state: SettingsUiState, onIntent: @escaping (SettingsIntent) -> Void) {
        self.state = state
        self.onIntent = onIntent
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                headline
                preferencesSection
                    // `UI_SPEC.md` §6.5: the sections start 16 pt below the title.
                    .padding(.top, MultiverseDimensions.spaceL)
                dataSection
                    .padding(.top, MultiverseDimensions.spaceXl)
                favoritesSection
                    .padding(.top, MultiverseDimensions.spaceXl)
            }
            .padding(.horizontal, MultiverseDimensions.spaceL)
            .padding(.bottom, MultiverseDimensions.spaceXl)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .cosmicCanvas()
        .alert(
            copy(SettingsCopy.deleteConfirmTitle),
            isPresented: confirmingDelete,
            actions: {
                Button(copy(SettingsCopy.cancel), role: .cancel) {
                    onIntent(SettingsScreen.deleteDismissedIntent())
                }
                Button(copy(SettingsCopy.delete), role: .destructive) {
                    onIntent(SettingsScreen.deleteConfirmedIntent())
                }
            },
            message: { Text(copy(SettingsCopy.deleteConfirmMessage)) }
        )
    }

    /// `UI_SPEC.md` §6.5's confirmation is presented exactly while the confirmation is open **and**
    /// favourites exist, so a state that claims both otherwise is reconciled here rather than
    /// rendered — the alert can never offer a Delete with nothing to delete
    /// (`AC-REQ-FUNC-035-3`). It is a static rule rather than an inline expression so it is assertable
    /// from a state alone.
    static func isConfirmationPresented(_ state: SettingsUiState) -> Bool {
        state.isConfirmingDelete && state.canDeleteFavorites
    }

    /// The destructive action is enabled exactly while there are favourites
    /// (`AC-REQ-FUNC-035-3`).
    static func isDeleteActionEnabled(_ state: SettingsUiState) -> Bool {
        state.canDeleteFavorites
    }

    /// The section's large title, at the same position as Discovery's (`UI_SPEC.md` §6.5, "Large
    /// Title, same position as Discovery"). The key is the destination's own, so Settings never
    /// borrows another section's word.
    private var headline: some View {
        Text(copy(SettingsCopy.title))
            .font(MultiverseType.largeTitleBold)
            .foregroundStyle(MultiverseLabelColors.primary)
            .padding(.top, MultiverseDimensions.spaceM)
    }

    // MARK: - Preferences

    /// The two-line Sounds row with the toggle as its trailing content (`UI_SPEC.md` §6.5).
    ///
    /// The toggle is off on a fresh install because the state it renders is the contract default
    /// (`AC-REQ-FUNC-033-2`), and it persists because the intent it sends is the one write path into
    /// the `IC-021` store. Its on-state track is Portal Green.
    private var preferencesSection: some View {
        SettingsSection(header: copy(SettingsCopy.preferencesSection)) {
            SettingsRow(
                symbol: "speaker.wave.2.fill",
                title: copy(SettingsCopy.soundsTitle),
                subtitle: copy(SettingsCopy.soundsBody)
            ) {
                Toggle(
                    copy(SettingsCopy.soundsTitle),
                    isOn: Binding(
                        get: { state.soundsEnabled },
                        set: { enabled in onIntent(SettingsScreen.soundsIntent(enabled: enabled)) }
                    )
                )
                .labelsHidden()
                .tint(MultiverseBrandColors.portalGreen)
                // The row's title is the control's label, so a screen reader announces "Sounds" with
                // the on/off state the toggle itself exposes (`UI_SPEC.md` §9).
                .accessibilityLabel(Text(copy(SettingsCopy.soundsTitle)))
            }
        }
    }

    // MARK: - Data

    /// The "Data source" row with the two-option picker below it, inside the same panel
    /// (`UI_SPEC.md` §6.5).
    ///
    /// The picker's selection is the state's protocol, so a change applies at once — the write goes
    /// out as `RemoteProtocolSelected` and the next character request uses the chosen protocol
    /// (`AC-REQ-FUNC-034-1`). Nothing else on the screen changes.
    private var dataSection: some View {
        SettingsSection(header: copy(SettingsCopy.dataSection)) {
            // The row says what the picker below decides (`CONF-80`, Figma `123:422`), and reads as one
            // element: its title and subtitle together.
            SettingsRow(
                symbol: "arrow.left.arrow.right",
                title: copy(SettingsCopy.dataSourceTitle),
                subtitle: copy(SettingsCopy.dataSourceBody)
            )
            .accessibilityElement(children: .combine)
            Picker(
                copy(SettingsCopy.dataSourceTitle),
                selection: Binding(
                    get: { SettingsDataSource.source(for: state.remoteProtocol) },
                    set: { source in
                        onIntent(SettingsScreen.dataSourceIntent(selecting: source.remoteProtocol))
                    }
                )
            ) {
                ForEach(SettingsDataSource.allCases) { source in
                    Text(copy(source.labelKey)).tag(source)
                }
            }
            .pickerStyle(.segmented)
            // The kit's Large segmented control (Figma `123:444`, 50 pt tall).
            .controlSize(.large)
            .labelsHidden()
            .accessibilityLabel(Text(copy(SettingsCopy.dataSourceTitle)))
            .padding(.horizontal, MultiverseDimensions.spaceL)
            .padding(.bottom, MultiverseDimensions.spaceL)
        }
    }

    // MARK: - Favorites

    /// The destructive action in its own panel, with the explanation as the section footer
    /// (`UI_SPEC.md` §6.5).
    ///
    /// The row **is** the button (`UI_SPEC.md` §4.2, `Row - Button`), so activating it dispatches the
    /// request and its disabled state follows `canDeleteFavorites` (`AC-REQ-FUNC-035-3`) — a disabled
    /// row cannot open a confirmation that has nothing to delete. The confirmation itself is drawn by
    /// this screen's `alert`.
    private var favoritesSection: some View {
        SettingsSection(
            header: copy(SettingsCopy.favoritesSection),
            footer: copy(SettingsCopy.deleteExplanation)
        ) {
            Button {
                onIntent(SettingsScreen.deleteRequestIntent())
            } label: {
                SettingsRow(title: copy(SettingsCopy.deleteAction), isDestructive: true)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(copy(SettingsCopy.deleteAction)))
        }
        .disabled(!SettingsScreen.isDeleteActionEnabled(state))
    }

    // MARK: - The confirmation

    /// The presentation `UI_SPEC.md` §6.5's alert is bound to: `isConfirmationPresented(state)`. A
    /// presentation-driven close — a tap outside or the system dismissal — reports the dismissal,
    /// which changes nothing (`AC-REQ-FUNC-035-1`).
    private var confirmingDelete: Binding<Bool> {
        Binding(
            get: { SettingsScreen.isConfirmationPresented(state) },
            set: { presented in
                if !presented { onIntent(SettingsScreen.deleteDismissedIntent()) }
            }
        )
    }

    private func copy(_ key: CopyKey) -> String {
        LocalizedCopy.shared.text(for: key)
    }
}

/// The canonical copy keys the iOS Settings screen binds (`CopyKeys`, `IC-017`, `UI_SPEC.md` §6.5).
///
/// The **values** live in the resource files `TASK-060` wires; this type carries only the names, so a
/// call site names a key instead of typing a string and `TEST-UNIT-036` keeps the two platforms
/// identical. It is the peer of `EmptyStateCopy`, which gives the two §6.4 placeholders the same
/// treatment.
enum SettingsCopy {
    static let title: CopyKey = .navSettings
    static let preferencesSection: CopyKey = .settingsSectionPreferences
    static let dataSection: CopyKey = .settingsSectionData
    static let favoritesSection: CopyKey = .navFavorites
    static let soundsTitle: CopyKey = .settingsSoundTitle
    static let soundsBody: CopyKey = .settingsSoundBody
    static let dataSourceTitle: CopyKey = .settingsDataSourceTitle
    static let dataSourceBody: CopyKey = .settingsDataSourceBody
    static let dataRest: CopyKey = .settingsDataRest
    static let dataGraphql: CopyKey = .settingsDataGraphql
    static let deleteAction: CopyKey = .settingsDeleteAction
    static let deleteExplanation: CopyKey = .settingsDeleteExplanation
    static let deleteConfirmTitle: CopyKey = .settingsDeleteConfirmTitle
    static let deleteConfirmMessage: CopyKey = .settingsDeleteConfirmMessage
    static let cancel: CopyKey = .actionCancel
    static let delete: CopyKey = .actionDelete

    /// Every key this screen binds, so its copy bindings are enumerable rather than assumed.
    static var all: [CopyKey] {
        var keys: [CopyKey] = []
        keys.append(title)
        keys.append(preferencesSection)
        keys.append(dataSection)
        keys.append(favoritesSection)
        keys.append(soundsTitle)
        keys.append(soundsBody)
        keys.append(dataSourceTitle)
        keys.append(dataSourceBody)
        keys.append(dataRest)
        keys.append(dataGraphql)
        keys.append(deleteAction)
        keys.append(deleteExplanation)
        keys.append(deleteConfirmTitle)
        keys.append(deleteConfirmMessage)
        keys.append(cancel)
        keys.append(delete)
        return keys
    }
}

/// The two data-source options of `UI_SPEC.md` §6.5's picker and the shared `RemoteProtocol` each
/// names (`IC-023`, `ADR-0011`).
///
/// The picker addresses its segments by a plain tag while the state carries the shared Kotlin enum,
/// so this is the only place the two are connected — the same shape
/// `CharacterPresentation.filterIdentifier` gives Discovery's segmented control.
enum SettingsDataSource: String, CaseIterable, Identifiable {
    case rest
    case graphql

    var id: String { rawValue }

    /// The canonical copy key of this option's label (`IC-017`).
    var labelKey: CopyKey {
        switch self {
        case .rest: return SettingsCopy.dataRest
        case .graphql: return SettingsCopy.dataGraphql
        }
    }

    /// The shared protocol this option selects.
    var remoteProtocol: RemoteProtocol {
        switch self {
        case .rest: return RemoteProtocol.rest
        case .graphql: return RemoteProtocol.graphql
        }
    }

    /// The option the state's protocol is, so the picker renders the protocol in force. REST is the
    /// fresh-install value (`AC-REQ-FUNC-034-1`), which is why it is the fallback here.
    static func source(for remoteProtocol: RemoteProtocol) -> SettingsDataSource {
        remoteProtocol == RemoteProtocol.graphql ? .graphql : .rest
    }
}

/// The intent each Settings control dispatches (`IC-023`).
///
/// They are the screen's own mapping from a control's event to the shared intent, kept here so it is
/// assertable without presenting a control — the same separation `ShellNavigation` gives the
/// navigation contract, and the reason a tap on "Delete" and a tap on "Cancel" can be proved to
/// dispatch different intents (`AC-REQ-FUNC-035-1`).
extension SettingsScreen {
    /// The Sounds toggle's event, whose write persists the preference (`AC-REQ-FUNC-033-1`).
    static func soundsIntent(enabled: Bool) -> SettingsIntent {
        SettingsIntentSoundsToggled(enabled: enabled)
    }

    /// The data-source picker's event, applied at once (`AC-REQ-FUNC-034-1`).
    static func dataSourceIntent(selecting remoteProtocol: RemoteProtocol) -> SettingsIntent {
        SettingsIntentRemoteProtocolSelected(protocol: remoteProtocol)
    }

    /// The destructive action's event, which opens the confirmation only when favourites exist.
    static func deleteRequestIntent() -> SettingsIntent {
        SettingsIntentDeleteFavoritesRequested.shared
    }

    /// The confirmation's "Delete", which clears every favourite (`AC-REQ-FUNC-035-2`).
    static func deleteConfirmedIntent() -> SettingsIntent {
        SettingsIntentDeleteFavoritesConfirmed.shared
    }

    /// The confirmation's "Cancel", a dismissal: it changes nothing (`AC-REQ-FUNC-035-1`).
    static func deleteDismissedIntent() -> SettingsIntent {
        SettingsIntentDeleteFavoritesDismissed.shared
    }
}

#Preview("Settings — fresh install") {
    SettingsScreen(
        state: SettingsUiState(
            soundsEnabled: false,
            remoteProtocol: RemoteProtocol.rest,
            canDeleteFavorites: false,
            isConfirmingDelete: false
        ),
        onIntent: { _ in }
    )
    .preferredColorScheme(.dark)
}

#Preview("Settings — favourites and the confirmation") {
    SettingsScreen(
        state: SettingsUiState(
            soundsEnabled: true,
            remoteProtocol: RemoteProtocol.graphql,
            canDeleteFavorites: true,
            isConfirmingDelete: true
        ),
        onIntent: { _ in }
    )
    .preferredColorScheme(.dark)
}

#Preview("Settings — largest Dynamic Type") {
    SettingsScreen(state: PreviewFixtures.settingsConfigured, onIntent: { _ in })
        .previewVariant(.largestDynamicType)
}

#Preview("Settings — Reduce Transparency") {
    SettingsScreen(state: PreviewFixtures.settingsConfigured, onIntent: { _ in })
        .previewVariant(.reduceTransparency)
}

#Preview("Settings — material fallback") {
    SettingsScreen(state: PreviewFixtures.settingsConfigured, onIntent: { _ in })
        .previewVariant(.materialFallback)
}

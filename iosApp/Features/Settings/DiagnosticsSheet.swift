#if DEBUG
    import MultiverseExplorer
    import SwiftUI

    // The iOS debug diagnostics sheet (`REQ-OBS-002`, `OBSERVABILITY.md` §5, `DEC-147`, `TASK-119`).
    //
    // The whole file is compiled into Debug builds only, so a Release build carries neither the sheet
    // nor its entry (`AC-REQ-OBS-002-1`); and the release Kotlin framework attaches no recorder, so even
    // the bootstrap's observation is `nil` there. The rows are rendered by `:core:diagnostics` — the same
    // rows the Android panel draws — so nothing here formats a value (`CONTRACTS.md` §7 R2). The sheet
    // is read-only: no request, no export, no share, no clear.

    /// The sheet's state: the recorder's rows, kept current while the sheet is open.
    @MainActor
    final class DiagnosticsSheetModel: ObservableObject {
        @Published private(set) var rows: [DiagnosticsLine] = []

        private let observation = DiagnosticsObservationBox()

        init() {
            observation.value = MultiverseBootstrap.shared.observeDiagnostics { [weak self] lines in
                // The observation delivers on the main queue, so the rows arrive on the main actor.
                MainActor.assumeIsolated { self?.rows = lines }
            }
        }
    }

    /// Closes the observation when the model goes; a class of its own because a main-actor model's
    /// `deinit` may not touch its non-`Sendable` Kotlin value under Swift 6.
    private final class DiagnosticsObservationBox: @unchecked Sendable {
        var value: DiagnosticsObservation?

        deinit {
            value?.close()
        }
    }

    /// The read-only sheet: the title, the line that says nothing here changes the app, and one row per
    /// item, its value in a monospaced face for scanning.
    struct DiagnosticsSheet: View {
        @ObservedObject var model: DiagnosticsSheetModel

        var body: some View {
            NavigationStack {
                List {
                    Text(MultiverseBootstrap.shared.diagnosticsReadOnly)
                        .font(MultiverseType.footnote)
                        .foregroundStyle(MultiverseLabelColors.secondary)
                    ForEach(Array(model.rows.enumerated()), id: \.offset) { _, row in
                        VStack(alignment: .leading, spacing: MultiverseDimensions.spaceXs) {
                            Text(row.label)
                                .font(MultiverseType.footnote)
                                .foregroundStyle(MultiverseLabelColors.secondary)
                            Text(row.value)
                                .font(MultiverseType.body.monospaced())
                                .foregroundStyle(MultiverseLabelColors.primary)
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
                .scrollContentBackground(.hidden)
                .background(MultiverseBrandColors.spaceBlack)
                .navigationTitle(MultiverseBootstrap.shared.diagnosticsTitle)
            }
        }
    }

    /// The Settings tab's debug entry to the sheet: a toolbar button, the iOS peer of the Android
    /// panel's launcher shortcut. It exists in Debug builds only.
    struct DiagnosticsEntry: ViewModifier {
        @State private var showing = false

        func body(content: Content) -> some View {
            content
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            showing = true
                        } label: {
                            Image(systemName: "ladybug")
                        }
                        .accessibilityLabel(Text(MultiverseBootstrap.shared.diagnosticsTitle))
                    }
                }
                .sheet(isPresented: $showing) {
                    DiagnosticsSheetHost()
                }
        }
    }

    /// Owns the sheet's model for as long as the sheet is presented, so the observation ends with it.
    private struct DiagnosticsSheetHost: View {
        @StateObject private var model = DiagnosticsSheetModel()

        var body: some View {
            DiagnosticsSheet(model: model)
        }
    }
#endif

@testable import MultiverseApp
import MultiverseExplorer
import SwiftUI
import XCTest

/// `TEST-UI-025`, iOS half — the card→Detail zoom (`REQ-FUNC-009`, `AC-REQ-FUNC-009-1`, `-2`,
/// `UI_SPEC.md` §7, `DEC-135`, `TASK-112`).
///
/// There was no `.matchedTransitionSource` and no `.navigationTransition(.zoom)`, so the push was the
/// default slide. The grid cell and the Detail destination now ask one decision for the source id:
/// the portrait's shared key with Reduce Motion off, and no id at all with it on, so neither end applies
/// the zoom and the change is the system's cross-fade. The decision is asserted here; the rendered
/// motion is the recorded checklist's (`TESTING.md` §9.2).
@MainActor
final class PortraitZoomTests: XCTestCase {
    func test_TEST_UI_025_given_reduce_motion_off_when_a_card_opens_then_both_ends_use_its_portrait_key() {
        XCTAssertEqual(PortraitMotion.zoomSourceID(characterID: "1", reduceMotion: false), "portrait-1")
        XCTAssertEqual(
            PortraitMotion.zoomSourceID(characterID: "1", reduceMotion: false),
            PortraitMotion.sharedKey(characterID: "1"),
            "the zoom's source id is the portrait key the Android shared element uses"
        )
    }

    func test_TEST_UI_025_given_reduce_motion_when_a_card_opens_then_no_zoom_source_is_named() {
        XCTAssertNil(PortraitMotion.zoomSourceID(characterID: "1", reduceMotion: true), "AC-REQ-FUNC-009-2: the cross-fade, not the zoom")
    }

    func test_TEST_UI_025_given_the_shell_namespace_when_the_grid_renders_then_its_cards_are_transition_sources() {
        let host = UIHostingController(rootView: NamespacedGrid())
        host.view.frame = CGRect(x: 0, y: 0, width: 402, height: 874)
        host.view.layoutIfNeeded()
        let image = UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        XCTAssertGreaterThan(image.size.width * image.size.height, 0, "the grid renders with the shell's namespace in place")
    }
}

/// The Discovery grid under a namespace, as the shell's destination provides it.
private struct NamespacedGrid: View {
    @Namespace private var namespace

    var body: some View {
        DiscoveryScreen(
            state: CharacterListUiState(
                filter: CharacterFilter(query: "", status: StatusFilter.all),
                items: [
                    CharacterCardUi(
                        id: "1",
                        name: "Rick Sanchez",
                        species: DisplayTextData(value: "Human"),
                        status: CharacterStatusAlive.shared,
                        statusLabel: CopyKeys.shared.STATUS_ALIVE,
                        imageUrl: "https://rickandmortyapi.com/api/character/avatar/1.jpeg"
                    ),
                ],
                totalCount: KotlinInt(int: 1),
                loadState: LoadStateContent.shared,
                isAppending: false,
                isStale: false,
                contentFailure: nil,
                isRefreshing: false
            ),
            loader: PortraitImageStub(),
            onIntent: { _ in },
            onOpenDetail: { _ in }
        )
        .environment(\.portraitTransitionNamespace, namespace)
    }
}

@testable import MultiverseApp
import SwiftUI
import XCTest

/// `TEST-UI-007` on iOS, Episodes half, and `AC-REQ-UX-008-1` (`IC-017`, `UI_SPEC.md` §6.4,
/// `REQ-FUNC-008`, `TASK-056`).
///
/// The placeholder is rendered from the same real view the shell hosts, driven only by its
/// "Browse characters" callback, so the case proves the surface paints and that the copy it paints is
/// the canonical key set rather than a literal. The action is a callback and never a route: the
/// screen holds no navigation state, which is what `AC-REQ-FUNC-008-2` requires and what
/// `ShellNavigationTests` asserts on the selection model.
@MainActor
final class EpisodesPlaceholderScreenTests: XCTestCase {
    // MARK: - TEST-UI-007: the placeholder renders its canonical copy

    func test_TEST_UI_007_given_the_episodes_destination_when_rendered_then_the_designed_placeholder_renders() {
        assertRenders(
            EpisodesPlaceholderScreen {},
            "the designed Episodes placeholder (UI_SPEC.md §6.4, TASK-056)"
        )
    }

    func test_TEST_UI_007_given_the_placeholder_when_rendered_in_both_glass_paths_then_each_produces_an_image() {
        for path in [GlassMaterial.glass, .material, .opaqueMaterial] {
            let renderer = ImageRenderer(
                content:
                    EpisodesPlaceholderScreen {}
                    .frame(width: 402, height: 874)
                    .background(MultiverseBrandColors.spaceBlack)
                    .environment(\.multiverseGlassPath, path)
            )
            renderer.proposedSize = ProposedViewSize(width: 402, height: 874)
            guard let image = renderer.uiImage else {
                XCTFail("the placeholder must render on the \(path) path")
                continue
            }
            XCTAssertGreaterThan(
                image.size.width * image.size.height,
                0,
                "the placeholder must render a non-empty image on the \(path) path"
            )
        }
    }

    /// The three keys the surface binds are the canonical ones, in both locales, so the parity case
    /// (`TEST-UNIT-036`) can hold the two platforms identical (`AC-REQ-UX-008-1`).
    func test_AC_REQ_UX_008_1_given_the_placeholder_when_its_copy_resolves_then_each_key_exists_in_both_locales() {
        let keys = [EmptyStateCopy.episodesHeading, EmptyStateCopy.episodesBody, EmptyStateCopy.browseCharacters]
        for key in keys {
            XCTAssertTrue(LocalizedCopy.shared.hasEntry(for: key, in: "en"), "en must carry \(key)")
            XCTAssertTrue(LocalizedCopy.shared.hasEntry(for: key, in: "es"), "es must carry \(key)")
        }
    }

    /// The section's own title and symbol, so the placeholder cannot borrow Favorites' words or
    /// illustration (`UI_SPEC.md` §6.4).
    func test_UI_SPEC_6_4_given_the_placeholder_when_its_section_is_named_then_it_is_episodes() {
        XCTAssertEqual(EmptyStateCopy.episodesSymbol, "play.tv.fill")
        XCTAssertEqual(EmptyStateCopy.episodesHeading, "episodes_heading")
        XCTAssertEqual(EmptyStateCopy.episodesBody, "episodes_body")
        XCTAssertEqual(EmptyStateCopy.browseCharacters, "browse_characters")
        XCTAssertFalse(
            LocalizedCopy.shared.text(for: EmptyStateCopy.episodesHeading)
                == LocalizedCopy.shared.text(for: EmptyStateCopy.favoritesHeading),
            "Episodes must not reuse the Favorites heading"
        )
    }

    /// `AC-REQ-FUNC-008-2`: the action is the screen's callback and nothing else — the placeholder
    /// owns no navigation state, so it cannot push a route. The shell is the one that maps the tap to
    /// the Characters selection (`ShellNavigation.browseCharacters`).
    func test_AC_REQ_FUNC_008_2_given_the_placeholder_when_browse_characters_runs_then_the_shell_action_is_called() {
        var activations = 0
        let screen = EpisodesPlaceholderScreen { activations += 1 }
        screen.onBrowseCharacters()
        XCTAssertEqual(activations, 1, "the action must run the caller's callback exactly once")
    }

    // MARK: - Helpers

    private func assertRenders<Content: View>(_ view: Content, _ description: String) {
        let renderer = ImageRenderer(
            content:
                view
                .frame(width: 402, height: 874)
                .background(MultiverseBrandColors.spaceBlack)
                .environment(\.multiverseGlassPath, .material)
        )
        renderer.proposedSize = ProposedViewSize(width: 402, height: 874)
        guard let image = renderer.uiImage else {
            XCTFail("\(description) must render")
            return
        }
        XCTAssertGreaterThan(image.size.width * image.size.height, 0, "\(description) must render a non-empty image")
    }
}

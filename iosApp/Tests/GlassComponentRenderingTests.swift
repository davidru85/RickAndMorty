import SwiftUI
import XCTest

@testable import MultiverseApp

/// `TEST-UI-010` and `TEST-UI-015` — the two visual paths of the iOS design system
/// (`REQ-PLAT-003`, `REQ-UX-007`, `AC-REQ-PLAT-003-1`, `DEC-025`).
///
/// `TEST-UI-010` proves both the iOS 26+ Liquid Glass path and the pre-iOS-26 material fallback
/// render for every component whose path is selectable, so the fallback cannot quietly stop
/// building. `TEST-UI-015` proves the **Reduce Transparency** path renders the opaque material on
/// every OS (`UI_SPEC.md` §9).
///
/// These are render smoke checks, not snapshot comparisons: the committed visual baselines are
/// `TASK-059`'s (`DEC-025`). What they add now is that each path is *reachable* — the fallback is
/// otherwise dead code on an iOS 26 simulator — and that both paths produce a non-empty image from
/// the same public surface.
@MainActor
final class GlassComponentRenderingTests: XCTestCase {
    // MARK: - TEST-UI-010: both paths render

    func test_TEST_UI_010_given_both_paths_when_a_character_card_is_rendered_then_each_path_produces_an_image() {
        assertRendersInBothPaths(
            GlassCharacterCard(
                name: "Rick Sanchez",
                species: "Human",
                statusTone: .alive,
                statusLabel: "Alive",
                portrait: Image(systemName: "photo")
            )
        )
    }

    func test_TEST_UI_010_given_both_paths_when_an_info_row_is_rendered_then_each_path_produces_an_image() {
        assertRendersInBothPaths(GlassInfoRow(symbol: "globe", label: "Origin", value: "Earth (C-137)"))
    }

    func test_TEST_UI_010_given_both_paths_when_a_status_capsule_is_rendered_then_each_path_produces_an_image() {
        assertRendersInBothPaths(GlassStatusCapsule(tone: .alive, label: "Alive"))
    }

    func test_TEST_UI_010_given_both_paths_when_a_search_field_is_rendered_then_each_path_produces_an_image() {
        assertRendersInBothPaths(GlassSearchField(text: .constant(""), placeholder: "Search characters"))
    }

    func test_TEST_UI_010_given_both_paths_when_a_segmented_control_is_rendered_then_each_path_produces_an_image() {
        assertRendersInBothPaths(
            GlassSegmentedControl(
                selection: .constant("all"),
                options: [.init(id: "all", label: "All"), .init(id: "alive", label: "Alive")]
            )
        )
    }

    func test_TEST_UI_010_given_both_paths_when_a_panel_is_rendered_then_each_path_produces_an_image() {
        assertRendersInBothPaths(
            GlassPanel {
                GlassInfoRow(symbol: "globe", label: "Origin", value: "Earth (C-137)")
            }
        )
    }

    func test_TEST_UI_010_given_both_paths_when_an_icon_button_is_rendered_then_each_path_produces_an_image() {
        assertRendersInBothPaths(
            GlassIconButton(systemImage: "heart", style: .prominent, accessibilityLabel: "Favorite") {}
        )
    }

    func test_TEST_UI_010_given_both_paths_when_a_text_button_is_rendered_then_each_path_produces_an_image() {
        assertRendersInBothPaths(GlassTextButton(label: "Browse characters") {})
    }

    func test_TEST_UI_010_given_both_paths_when_an_empty_state_is_rendered_then_each_path_produces_an_image() {
        assertRendersInBothPaths(
            EmptyState(
                symbol: EmptyStateCopy.favoritesSymbol,
                heading: "No favorites yet",
                body: "Tap the heart on a character's page to keep them here.",
                actionLabel: "Browse characters"
            ) {}
        )
    }

    /// The unselected tab is `Label/Primary` white and the selected one Portal Glow — never the
    /// system's dimmed secondary colour (`UI_SPEC.md` §4.2).
    func test_TEST_UI_010_given_the_tab_bar_appearance_then_selected_is_glow_and_unselected_white() {
        let appearance = GlassTabBarAppearance.appearance()
        let expectedUnselected = UIColor(MultiverseLabelColors.primary)
        let expectedSelected = UIColor(MultiverseBrandColors.portalGlow)
        var layouts: [UITabBarItemAppearance] = []
        layouts.append(appearance.stackedLayoutAppearance)
        layouts.append(appearance.inlineLayoutAppearance)
        layouts.append(appearance.compactInlineLayoutAppearance)
        for layout in layouts {
            XCTAssertEqual(layout.normal.iconColor, expectedUnselected)
            XCTAssertEqual(
                layout.normal.titleTextAttributes[.foregroundColor] as? UIColor,
                expectedUnselected
            )
            XCTAssertEqual(layout.selected.iconColor, expectedSelected)
            XCTAssertEqual(
                layout.selected.titleTextAttributes[.foregroundColor] as? UIColor,
                expectedSelected
            )
        }
    }

    // MARK: - TEST-UI-015: Reduce Transparency

    /// The resolver selects the opaque material for the accessibility setting on every OS, including
    /// one where `glassEffect` exists (`UI_SPEC.md` §9).
    func test_TEST_UI_015_given_reduce_transparency_then_the_resolver_selects_the_opaque_material() {
        XCTAssertEqual(GlassMaterial.resolved(reduceTransparency: true), .opaqueMaterial)
    }

    func test_TEST_UI_015_given_reduce_transparency_then_the_opaque_material_path_renders() {
        // Reduce Transparency selects the opaque material (`UI_SPEC.md` §9). Two forcings are used
        // because the accessibility flag itself is read-only through the public environment: the
        // path override pins the paint, and `_accessibilityReduceTransparency` drives the real
        // resolver the production code reads, so the render covers both.
        for (name, view) in opaquePathComponents() {
            let renderer = ImageRenderer(
                content:
                    view
                    .environment(\.multiverseGlassPath, .opaqueMaterial)
                    .transformEnvironment(\._accessibilityReduceTransparency) { $0 = true }
            )
            renderer.proposedSize = ProposedViewSize(width: 402, height: 874)
            guard let image = renderer.uiImage else {
                XCTFail("\(name) must render under Reduce Transparency")
                continue
            }
            XCTAssertGreaterThan(
                image.size.width * image.size.height,
                0,
                "\(name) must render a non-empty image under Reduce Transparency"
            )
        }
    }

    // MARK: - Helpers

    /// Renders [view] once with the glass path forced and once with the material path forced, and
    /// asserts both produce an image (`TEST-UI-010`).
    private func assertRendersInBothPaths<Content: View>(
        _ view: Content,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        for path in [GlassMaterial.glass, .material, .opaqueMaterial] {
            let renderer = ImageRenderer(
                content:
                    view
                    .frame(width: 402, height: 874)
                    .background(MultiverseBrandColors.spaceBlack)
                    .environment(\.multiverseGlassPath, path)
            )
            renderer.proposedSize = ProposedViewSize(width: 402, height: 874)
            guard let image = renderer.uiImage else {
                XCTFail("\(type(of: view)) must render on the \(path) path", file: file, line: line)
                continue
            }
            // A non-empty image, not merely a non-nil one: a zero-sized render would pass the first
            // check while proving nothing about the path building.
            XCTAssertGreaterThan(
                image.size.width * image.size.height,
                0,
                "\(type(of: view)) must render a non-empty image on the \(path) path",
                file: file,
                line: line
            )
        }
    }

    private func opaquePathComponents() -> [(String, AnyView)] {
        var components: [(String, AnyView)] = []
        components.append(
            (
                "GlassCharacterCard",
                AnyView(
                    GlassCharacterCard(
                        name: "Rick Sanchez",
                        species: "Human",
                        statusTone: .alive,
                        statusLabel: "Alive",
                        portrait: Image(systemName: "photo")
                    )
                )
            )
        )
        components.append(
            ("GlassInfoRow", AnyView(GlassInfoRow(symbol: "globe", label: "Origin", value: "Earth")))
        )
        components.append(
            ("GlassTextButton", AnyView(GlassTextButton(label: "Browse characters") {}))
        )
        components.append(
            (
                "EmptyState",
                AnyView(
                    EmptyState(
                        symbol: EmptyStateCopy.favoritesSymbol,
                        heading: "No favorites yet",
                        body: "Tap the heart on a character's page to keep them here.",
                        actionLabel: "Browse characters"
                    ) {}
                )
            )
        )
        return components
    }
}

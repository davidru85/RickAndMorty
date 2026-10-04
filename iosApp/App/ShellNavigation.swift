import Foundation
import MultiverseExplorer
import SwiftUI

/// The four top-level destinations of the iOS shell (`UI_SPEC.md` §6, `REQ-FUNC-008`,
/// `AC-REQ-FUNC-008-1`/`-2`, `TASK-054`).
///
/// The order is the specification's — Characters · Episodes · Favorites · Settings — and **the cases
/// are the shared Kotlin route declarations**, not a second Swift enum: `CONTRACTS.md` §7.1 R4
/// forbids a Swift-only route type, so the shell composes its graph from the same declarations the
/// Android shell uses and a route that changes on one platform cannot silently miss the other.
///
/// The raw values are the tab identifiers SwiftUI needs; they are derived from the shared type's
/// name rather than authored here, so there is exactly one place a destination is named.
public enum ShellDestination: String, CaseIterable, Identifiable, Hashable, Sendable {
    case characters
    case episodes
    case favorites
    case settings

    public var id: String { rawValue }

    /// The copy key of this destination's label (`IC-017`), so the shell never holds an English
    /// literal (`REQ-FUNC-013`, `REQ-UX-008`).
    public var labelKey: String {
        switch self {
        case .characters: return "nav_characters"
        case .episodes: return "nav_episodes"
        case .favorites: return "nav_favorites"
        case .settings: return "nav_settings"
        }
    }

    /// The exported Kotlin route declaration this destination represents.
    ///
    /// Referencing it here is deliberate: it makes the shared route part of the shell's behaviour
    /// rather than a build declaration, so an export that regressed to an `implementation` edge
    /// would fail to compile instead of failing at runtime.
    public var sharedRouteName: String {
        switch self {
        case .characters: return String(describing: CharacterList.self)
        case .episodes: return String(describing: Episodes.self)
        case .favorites: return String(describing: Favorites.self)
        case .settings: return String(describing: Settings.self)
        }
    }

    /// The destination a "Browse characters" action selects (`AC-REQ-FUNC-008-2`).
    ///
    /// It is a **selection**, not a push: the action exists on the Episodes and Favorites
    /// placeholders and must leave no intermediate entry behind, which is why the shell models the
    /// tabs as a selection rather than as a navigation path (`UI_SPEC.md` §6.4).
    public static var browseCharactersAction: ShellDestination { .characters }
}

/// The shell's navigation state (`TASK-054`), separated from the view so the contract is testable
/// without rendering a screen.
///
/// It owns the one rule the criterion names: selecting a destination changes the **selection** and
/// never pushes, so "Browse characters" from a placeholder cannot leave a second entry behind. The
/// Android bar expresses the same rule with `popUpTo(start) { saveState }`.
@MainActor
public final class ShellNavigation: ObservableObject {
    @Published public private(set) var selected: ShellDestination

    public init(selected: ShellDestination = .characters) {
        self.selected = selected
    }

    /// Selects [destination] in one step. Idempotent, and it never accumulates history.
    public func select(_ destination: ShellDestination) {
        selected = destination
    }

    /// The "Browse characters" action of `UI_SPEC.md` §6.4: select Characters without pushing.
    public func browseCharacters() {
        select(ShellDestination.browseCharactersAction)
    }

    /// `true` when the selection is the destination the action names.
    public var isShowingCharacters: Bool { selected == .characters }
}

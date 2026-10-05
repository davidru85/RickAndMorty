import Foundation
import MultiverseExplorer

/// The seam between Swift and the shared Kotlin framework (`ADR-0012`, `ADR-0003`, `TASK-051`).
///
/// The app links exactly one Kotlin framework — `MultiverseExplorer`, produced by `:core:ios` — and
/// this file is what makes that linkage **load-bearing** rather than declared: a target that names
/// the framework but references no symbol from it links nothing, and `otool -L` shows no Kotlin
/// runtime in the product. Naming the exported types here is therefore the evidence the ADR asks
/// for, and it fails at compile time if an export regresses to an `implementation` edge.
///
/// It reaches the shared surface directly and does not re-implement it: the route declarations are
/// Kotlin types crossing the boundary (`CONTRACTS.md` §7.1 R4), so the Swift shell composes its
/// navigation graph from the same declarations the Android shell uses, and no Swift-only route enum
/// is introduced.
enum SharedFramework {
    /// The route declarations the shell composes, taken from the shared framework.
    static func exportedRoutes() -> [String] {
        MultiverseBootstrap.shared.routes
    }

    /// One character card in the shared vocabulary (`IC-016`), built by the shared code.
    static func card(
        id: String,
        name: String,
        species: String,
        statusLabelKey: String,
        imageUrl: String
    ) -> CharacterCardUi {
        MultiverseBootstrap.shared.card(
            id: id,
            name: name,
            species: species,
            statusLabelKey: statusLabelKey,
            imageUrl: imageUrl
        )
    }

    /// The repository interface the iOS composition root resolves against (`DEC-091`).
    static func repositoryType() -> String {
        MultiverseBootstrap.shared.repositoryType()
    }
}

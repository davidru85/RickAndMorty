package io.github.davidru85.multiverse.core.ios

import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import io.github.davidru85.multiverse.feature.characterdetail.navigation.CharacterDetail
import io.github.davidru85.multiverse.feature.discovery.navigation.CharacterList

/**
 * The `:core:ios` bootstrap (`ADR-0012`, `TASK-078`).
 *
 * The module's reason to exist is the framework binary its build script declares, and a Kotlin
 * Multiplatform module with an empty source set does not produce one: the linker task is `NO-SOURCE`
 * and no `.framework` is written, so a module that exported everything and compiled nothing would
 * satisfy the boundary rules while producing nothing the iOS app could link. This file is therefore
 * the module's one type, and it earns its place by doing the two jobs the export exists to serve.
 *
 * 1. **It proves the exported surface in Kotlin.** The framework's `api` graph is a build
 *    declaration; a compile-time reference to a type from each exported module is what turns that
 *    declaration into evidence, and it fails the build — rather than the Xcode session — if an
 *    export regresses to an `implementation` edge.
 * 2. **It gives the Swift side one entry point** to the shared graph it must start
 *    (`DESIGN.md` §5): the shell resolves its state holders against `:core:domain` interfaces, so the
 *    bootstrapper is what names them without any feature naming an implementation.
 *
 * It carries no behaviour of its own beyond assembling that surface, and it is the only file in the
 * module.
 */
public object MultiverseBootstrap {
    /**
     * The exported route declarations, in the shell's tab order (`UI_SPEC.md` §6, `DESIGN.md` §4.2).
     *
     * Each is a type from a different `:feature:*` module, so referencing them together is what makes
     * the export list compile-checked: an export that regressed to `implementation` would make this
     * declaration unresolved.
     */
    public val routes: List<String> =
        listOf(
            CharacterList::class.qualifiedName.orEmpty(),
            CharacterDetail::class.qualifiedName.orEmpty(),
        )

    /**
     * The identity of one character card in the shared vocabulary (`IC-016`, `:core:presentation`),
     * so the Swift side can be handed a card without reconstructing it.
     */
    public fun card(
        id: String,
        name: String,
        species: String,
        statusLabelKey: String,
        imageUrl: String,
    ): CharacterCardUi =
        CharacterCardUi(
            id = CharacterId(id),
            name = name,
            species = io.github.davidru85.multiverse.core.presentation.DisplayText.Data(species),
            status = io.github.davidru85.multiverse.core.domain.model.CharacterStatus.Unknown,
            statusLabel = io.github.davidru85.multiverse.core.presentation.CopyKey(statusLabelKey),
            imageUrl = imageUrl,
        )

    /**
     * The type the composition root resolves (`:core:domain`), named here so the exported graph
     * carries the repository interface the Swift shell starts its graph around (`DEC-091`).
     */
    public fun repositoryType(): String = CharacterRepository::class.qualifiedName.orEmpty()
}

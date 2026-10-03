package io.github.davidru85.multiverse.feature.discovery.di

import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.feature.discovery.domain.GetCharacterPage
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/**
 * The Discovery module's own Koin bindings (`DESIGN.md` §5, `DEC-099`): the feature-local use case and
 * the pager factory its state holder consumes.
 *
 * It binds against `:core:domain` interfaces only — `CharacterRepository`, `CharacterPager` — so no
 * implementation type of `:core:data` is named here and the composition root stays the one place that
 * supplies them (`DEC-091`, ADR-0014). The pager is a **factory**, not a singleton: `IC-014` belongs to
 * one state-holder scope, so the caller passes that scope's [CoroutineScope] as a parameter.
 */
public val discoveryModule: Module =
    module {
        single { GetCharacterPage(repository = get<CharacterRepository>()) }

        /** One pager per state-holder scope; the caller supplies its scope (`IC-014`, `TASK-039`). */
        factory<CharacterPager> { (scope: CoroutineScope) -> get<CharacterPager> { parametersOf(scope) } }
    }

package io.github.davidru85.multiverse.feature.discovery.di

import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.feature.discovery.domain.GetCharacterPage
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Discovery module's own Koin bindings (`DESIGN.md` §5, `DEC-099`): the feature-local use case its
 * state holder consumes.
 *
 * It binds against `:core:domain` interfaces only — `CharacterRepository` — so no implementation type
 * of `:core:data` is named here and the composition root stays the one place that supplies them
 * (`DEC-091`, ADR-0014).
 *
 * It does **not** re-declare the pager: `coreModule` already binds `factory<CharacterPager>` taking
 * the caller's `CoroutineScope` (`IC-014`, one pager per state-holder scope). Re-declaring it here
 * made the factory resolve itself, which is a stack overflow rather than a bound graph.
 */
public val discoveryModule: Module =
    module {
        single { GetCharacterPage(repository = get<CharacterRepository>()) }
    }

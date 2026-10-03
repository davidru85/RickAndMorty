package io.github.davidru85.multiverse.feature.discovery.presentation

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.paging.CharacterPager
import io.github.davidru85.multiverse.core.domain.paging.PagerState
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.testing.FakeCharacterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The behavioural double of `IC-014` for the Discovery state-holder tests.
 *
 * A feature may not depend on `:core:data`, where `IC-014` is implemented (R8, `DEC-091`), so the
 * precedence, debounce and cancellation cases drive the real `PagerState` contract through this
 * small, honest stand-in: it accumulates pages from the `IC-007` double
 * ([FakeCharacterRepository]), reports a failure as a value rather than throwing, replaces the
 * collection on `setFilter`, cancels a superseded load so it cannot publish, coalesces `next()` and
 * reports `isEndReached` from the server's `nextPage == null`.
 *
 * It is not evidence for the production pager: `RepositoryCharacterPager`'s own behaviour is
 * `TEST-UNIT-016`'s and `TEST-UNIT-021`'s, against the real implementation. This double exists so the
 * Discovery mapping is asserted over the published contract rather than over a mock of it.
 */
public class FakeCharacterPager(
    private val repository: FakeCharacterRepository,
    private val scope: CoroutineScope,
) : CharacterPager {
    private val mutableState =
        MutableStateFlow(
            PagerState(
                filter = CharacterFilter(),
                items = emptyList(),
                totalCount = null,
                isAppending = false,
                isEndReached = false,
                isStale = false,
                failure = null,
            ),
        )

    override val state: StateFlow<PagerState> = mutableState.asStateFlow()

    private var generation = 0L
    private var nextPage: Int? = FIRST_PAGE
    private var inFlight: Job? = null
    private var failedLoad: Load? = null

    /** One page request: page 1 replaces the collection, any later page appends to it. */
    private data class Load(
        val page: Int,
        val policy: PageLoadPolicy,
    ) {
        val replaces: Boolean get() = page == FIRST_PAGE
    }

    override suspend fun setFilter(filter: CharacterFilter) {
        inFlight?.cancel()
        generation++
        nextPage = FIRST_PAGE
        failedLoad = null
        mutableState.value =
            PagerState(
                filter = filter,
                items = emptyList(),
                totalCount = null,
                isAppending = false,
                isEndReached = false,
                isStale = false,
                failure = null,
            )
        start(Load(FIRST_PAGE, PageLoadPolicy.Default), filter).join()
    }

    override suspend fun next() {
        val current = mutableState.value
        if (current.failure != null || nextPage == null) return
        inFlight?.takeIf { it.isActive }?.let {
            it.join()
            return
        }
        val load = Load(nextPage ?: FIRST_PAGE, PageLoadPolicy.Default)
        if (!load.replaces) mutableState.update { it.copy(isAppending = it.items.isNotEmpty()) }
        start(load, current.filter).join()
    }

    override suspend fun refresh() {
        inFlight?.cancel()
        generation++
        nextPage = FIRST_PAGE
        start(Load(FIRST_PAGE, PageLoadPolicy.ForceNetwork), mutableState.value.filter).join()
    }

    override suspend fun retry() {
        if (mutableState.value.failure == null) return
        val load = failedLoad ?: return
        start(load, mutableState.value.filter).join()
    }

    private fun start(
        load: Load,
        filter: CharacterFilter,
    ): Job {
        val startedGeneration = generation
        return scope
            .launch {
                val result = repository.page(filter, load.page, load.policy)
                if (startedGeneration != generation) return@launch
                publish(load, filter, result)
            }.also { inFlight = it }
    }

    private fun publish(
        load: Load,
        filter: CharacterFilter,
        result: DataResult<io.github.davidru85.multiverse.core.domain.model.CharacterPage>,
    ) {
        when (result) {
            is DataResult.Success -> {
                val page = result.value
                mutableState.value =
                    PagerState(
                        filter = filter,
                        items = if (load.replaces) page.characters else mutableState.value.items + page.characters,
                        totalCount = page.totalCount ?: mutableState.value.totalCount,
                        isAppending = false,
                        isEndReached = page.nextPage == null,
                        isStale = result.isStale,
                        failure = null,
                    )
                nextPage = page.nextPage
                failedLoad = null
            }

            is DataResult.Failure ->
                if (!load.replaces && result.failure is ApiFailure.NotFound) {
                    // A paging 404 reached through a valid sequence is the end (`ERROR_FLOW.md` §5.2).
                    nextPage = null
                    failedLoad = null
                    mutableState.update { it.copy(isAppending = false, isEndReached = true, failure = null) }
                } else {
                    failedLoad = load
                    mutableState.update { it.copy(isAppending = false, failure = result.failure) }
                }
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}

package io.github.davidru85.multiverse.core.domain

import io.github.davidru85.multiverse.core.domain.model.CharacterDetails
import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.domain.model.CharacterPage
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.repository.PageLoadPolicy
import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import io.github.davidru85.multiverse.core.domain.result.DataSource
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `IC-007` as amended by `DEC-086`: a caller that omits the policy asks for `Default`, and the
 * policy set is exactly `Default` and `ForceNetwork`. The default value is part of the contract a
 * Swift consumer cannot see (`CONTRACTS.md` §8.2 B6), so it is pinned here.
 */
class CharacterRepositoryTest {
    private class RecordingRepository : CharacterRepository {
        val policies = mutableListOf<PageLoadPolicy>()

        override suspend fun page(
            filter: CharacterFilter,
            page: Int,
            policy: PageLoadPolicy,
        ): DataResult<CharacterPage> {
            policies += policy
            return DataResult.Failure(ApiFailure.Offline, DataSource.NETWORK)
        }

        override suspend fun details(
            id: CharacterId,
            enrich: Boolean,
        ): DataResult<CharacterDetails> = DataResult.Failure(ApiFailure.Offline, DataSource.NETWORK)
    }

    private fun runSuspending(block: suspend () -> Unit) {
        block.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() })
    }

    @Test
    fun `TEST-UNIT-052 given_a_caller_that_omits_the_policy_when_a_page_is_requested_then_the_default_policy_is_used`() {
        val repository = RecordingRepository()
        runSuspending {
            repository.page(CharacterFilter(), 1)
            repository.page(CharacterFilter(), 1, PageLoadPolicy.ForceNetwork)
        }
        assertEquals(
            listOf(PageLoadPolicy.Default, PageLoadPolicy.ForceNetwork),
            repository.policies,
            "TEST-UNIT-052: an omitted policy is Default (DEC-086)",
        )
        assertEquals(
            listOf(PageLoadPolicy.Default, PageLoadPolicy.ForceNetwork),
            PageLoadPolicy.entries,
            "TEST-UNIT-052: exactly two page-load policies",
        )
    }
}

package io.github.davidru85.multiverse.app.splash

import io.github.davidru85.multiverse.core.domain.model.CharacterFilter
import io.github.davidru85.multiverse.core.domain.repository.CharacterRepository
import io.github.davidru85.multiverse.core.domain.result.DataResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The splash readiness gate (`DEC-098`, `UI_SPEC.md` §6.1): the splash lasts **at least 1.2 s** so the
 * portal's acceleration completes, and **at most 3 s**, and it waits for the first page of characters
 * rather than for a fixed delay.
 *
 * The policy is a race between two suspending bounds, so **one clock governs both**: the first page
 * starts immediately under the ceiling, the minimum elapses via `delay`, and the gate then returns the
 * page's outcome. That is why the timing is exactly the specification's — data ready sooner still waits
 * the minimum, data slower keeps the splash until it settles, and data that never settles is abandoned
 * at the ceiling by the timeout.
 *
 * It completes on the **first-page outcome** — success *or* failure — because a failure is a result
 * Discovery renders, not a reason to hold the splash (`AC-REQ-FUNC-007-2`). An earlier draft measured
 * elapsed time with a monotonic clock while delaying on the coroutine clock; the two disagreed, which
 * `TEST-UI-006` reproduced as a gate that never completed at the documented instant.
 *
 * Both the dispatcher and the bounds are injected, and no wall clock is read, so the timing policy is
 * proved on virtual time and never waits in a test. The gate is shell-internal: it crosses no module
 * boundary, which is why it has no `IC-###` (`DEC-098`).
 */
public class SplashGate(
    private val repository: CharacterRepository,
    private val dispatcher: CoroutineDispatcher,
    public val minimum: Duration = MINIMUM,
    public val maximum: Duration = MAXIMUM,
) {
    /**
     * Suspends until the splash may be dismissed. It returns the first page's outcome, or `null` when
     * the ceiling expired before the page settled; the caller renders Discovery either way.
     */
    public suspend fun awaitReady(): DataResult<*>? =
        coroutineScope {
            val firstPage = async(dispatcher) { firstPageUnderCeiling() }
            delay(minimum)
            firstPage.await()
        }

    /** The first page, or `null` when the ceiling expires before it settles. */
    private suspend fun firstPageUnderCeiling(): DataResult<*>? =
        try {
            withTimeout(maximum) { repository.page(CharacterFilter(), page = 1) }
        } catch (ceilingReached: TimeoutCancellationException) {
            null
        }

    public companion object {
        /** The acceleration of the portal needs this long (`UI_SPEC.md` §6.1). */
        public val MINIMUM: Duration = 1_200.milliseconds

        /** The ceiling: a cold start with no network must not hold the splash (`AC-REQ-FUNC-007-2`). */
        public val MAXIMUM: Duration = 3_000.milliseconds
    }
}

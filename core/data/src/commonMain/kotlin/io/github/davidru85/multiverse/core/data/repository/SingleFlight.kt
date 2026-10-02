package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.data.logging.CorrelationId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Concurrent calls with the same key share one execution (`REQ-REL-002`, `IC-007`).
 *
 * Scope ownership, decided before the first line of it was written (`TASK-038`):
 *
 * - the shared work runs in a supervisor child of [owner], so closing the owner cancels it, and a
 *   failure of one key's work never cancels another's;
 * - a waiter that is cancelled stops waiting; the work goes on while another waiter remains, and is
 *   cancelled when the last one leaves, so nobody pays for work nobody waits for;
 * - an entry is removed when its last waiter leaves, and a finished entry is never joined, so a later
 *   call with the same key runs again — coalescing is not caching.
 *
 * The shared work runs in the correlation scope of the call that started it, or a new one; a call
 * that joins it is reported to [onJoin] with that scope's id, so the duplicate names the request it
 * joined (`LOG-012`).
 */
internal class SingleFlight<K : Any, V>(
    owner: CoroutineScope,
    private val onJoin: (key: K, correlationId: String) -> Unit,
) {
    private val scope = CoroutineScope(owner.coroutineContext + SupervisorJob(owner.coroutineContext[Job]))
    private val mutex = Mutex()
    private val flights = mutableMapOf<K, Flight<V>>()

    private class Flight<V>(
        val work: Deferred<V>,
        val correlationId: CorrelationId,
    ) {
        var waiters = 0
    }

    suspend fun run(
        key: K,
        block: suspend () -> V,
    ): V {
        val caller = currentCoroutineContext()[CorrelationId]
        val (flight, joined) =
            mutex.withLock {
                val joinable = flights[key]?.takeUnless { it.work.isCompleted }
                val flight =
                    joinable ?: (caller ?: CorrelationId.next()).let { id ->
                        Flight(scope.async(id, start = CoroutineStart.LAZY) { block() }, id).also { flights[key] = it }
                    }
                flight.waiters++
                flight to (joinable != null)
            }
        if (joined) onJoin(key, flight.correlationId.value)
        flight.work.start()
        try {
            return flight.work.await()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    flight.waiters--
                    if (flight.waiters == 0) {
                        if (flights[key] === flight) flights.remove(key)
                        if (!flight.work.isCompleted) flight.work.cancel()
                    }
                }
            }
        }
    }
}

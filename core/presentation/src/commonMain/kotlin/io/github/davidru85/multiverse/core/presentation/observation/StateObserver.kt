package io.github.davidru85.multiverse.core.presentation.observation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Observes a [StateFlow] for a platform state holder (`DEC-143`, `IC-014`): [onEach] receives the
 * current value, then each new one, on [dispatcher], until [close].
 *
 * It is the hand-written bridge `DEC-013` allows: the iOS holders stay Swift and platform-owned, and
 * this is how they hear a shared reducer's state change instead of reading it every frame. On iOS the
 * dispatcher is the main queue, so each value arrives where SwiftUI reads it. A `StateFlow` delivers
 * only values that differ from the last, so an unchanged state calls nothing back.
 */
public class StateObserver<T>(
    flow: StateFlow<T>,
    dispatcher: CoroutineDispatcher,
    onEach: (T) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        scope.launch { flow.collect { onEach(it) } }
    }

    /** Stops the observation; nothing is delivered afterwards. Idempotent. */
    public fun close() {
        scope.cancel()
    }
}

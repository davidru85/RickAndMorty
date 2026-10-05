package io.github.davidru85.multiverse.core.presentation.observation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TEST-UNIT-094` — the state observer the iOS holders use instead of polling (`DEC-143`, `TASK-115`).
 *
 * Each Swift holder read its `StateFlow` every 16 ms for as long as it lived, so the four tab holders
 * woke the main actor about 240 times a second even with nothing changing. The observer collects the
 * flow and calls back once per new value, and nothing after it is closed. The case drives it on the
 * test scheduler, so "once per value" and "nothing after close" are exact.
 */
class StateObserverTest {
    @Test
    fun `TEST-UNIT-094 given_a_state_flow_when_observed_then_each_new_value_is_delivered_once`() =
        runTest {
            val flow = MutableStateFlow(1)
            val seen = mutableListOf<Int>()
            val observer = StateObserver(flow, StandardTestDispatcher(testScheduler)) { seen += it }

            runCurrent()
            flow.value = 2
            runCurrent()
            flow.value = 2
            flow.value = 3
            runCurrent()

            assertEquals(listOf(1, 2, 3), seen, "the current value first, then each change once")
            observer.close()
        }

    @Test
    fun `TEST-UNIT-094 given_a_closed_observer_when_the_flow_changes_then_nothing_is_delivered`() =
        runTest {
            val flow = MutableStateFlow("a")
            val seen = mutableListOf<String>()
            val observer = StateObserver(flow, StandardTestDispatcher(testScheduler)) { seen += it }
            runCurrent()

            observer.close()
            flow.value = "b"
            runCurrent()

            assertEquals(listOf("a"), seen, "a closed observer delivers nothing more")
        }
}

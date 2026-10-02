package io.github.davidru85.multiverse.testing

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Time and dispatcher control (`TESTING.md` §5, `REQ-REL-004`, `TASK-024`).
 *
 * Every suspending subject under test runs on an injected `TestDispatcher`; production code takes
 * a dispatcher provider rather than reaching for `Dispatchers.Default`. Virtual time is advanced
 * explicitly, so the 300 ms debounce (`REQ-FUNC-003`), the bounded backoff (`API_SPECS.md` §6.3)
 * and the cache windows are asserted without a real wait. No test in this repository calls
 * `delay` or `Thread.sleep`.
 */
public object TestTime {
    /** A single-threaded dispatcher whose virtual clock only advances when a test says so. */
    public fun dispatcher(): TestDispatcher = StandardTestDispatcher()

    /**
     * Runs [body] on a fresh [TestDispatcher] with virtual time.
     *
     * The dispatcher is created here rather than injected so a test cannot accidentally share
     * virtual time with a neighbouring test; the body still receives it, because the subject under
     * test has to be constructed with the same dispatcher the scope uses.
     */
    public fun run(body: suspend TestScope.(TestDispatcher) -> Unit) {
        val dispatcher = dispatcher()
        runTest(dispatcher) { body(dispatcher) }
    }
}

/**
 * An injectable clock, so cache freshness and staleness are computed from a value a test controls
 * rather than from the wall clock (`DEC-012`, `DEC-018`, `REQ-REL-004`).
 *
 * The interface is deliberately one method wide: nothing in the harness needs a calendar, a
 * timezone or a date format, and a wider clock would invite a test to depend on them.
 */
public fun interface FakeClock {
    /** The current instant, in epoch milliseconds. */
    public fun nowMillis(): Long
}

/**
 * A [FakeClock] a test moves explicitly.
 *
 * The contract is `nowMillis()` plus an explicit advance; a system-clock change never alters an
 * assertion because nothing here reads the system clock. It is also a `kotlin.time.Clock`, the type
 * production code is injected with (`TASK-038`), so one fake serves both.
 */
public class MutableFakeClock(
    private var currentMillis: Long = 0L,
) : FakeClock,
    Clock {
    override fun nowMillis(): Long = currentMillis

    override fun now(): Instant = Instant.fromEpochMilliseconds(currentMillis)

    /** Moves the clock forward by [millis]. Negative values are rejected: time does not run back. */
    public fun advanceBy(millis: Long): MutableFakeClock {
        require(millis >= 0) { "A fake clock cannot move backwards ($millis ms)" }
        currentMillis += millis
        return this
    }

    /** Moves the clock to [millis]; a target in the past is rejected for the same reason. */
    public fun setTo(millis: Long): MutableFakeClock {
        require(millis >= currentMillis) { "A fake clock cannot move backwards ($currentMillis -> $millis ms)" }
        currentMillis = millis
        return this
    }
}

package io.github.davidru85.multiverse.core.data.repository

import io.github.davidru85.multiverse.core.domain.result.ApiFailure
import io.github.davidru85.multiverse.core.domain.result.DataResult
import kotlinx.coroutines.delay
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * The one retry layer of the data path (`API_SPECS.md` §6.3, `DEC-084`).
 *
 * An eligible transient failure — `Offline`, `Timeout`, `Server` — is retried at most twice, so a
 * sequence makes **at most three attempts in total**, waiting about 500 ms and then 1,500 ms, each
 * scaled by a factor drawn uniformly from [0.8, 1.2] from [random]. A `RateLimited` failure is retried
 * at most once, and only after readable advice of at most 60 seconds, which is waited exactly. Every
 * other failure is returned after one attempt. A success ends the sequence; each [run] is a fresh
 * budget; waits are cancellable, and a cancellation is never turned into a failure.
 *
 * The engine does not retry underneath this layer (`apiOkHttpClient`), so the budget is not multiplied.
 */
internal class RetryPolicy(
    private val random: Random,
) {
    suspend fun <T> run(attempt: suspend () -> DataResult<T>): DataResult<T> {
        var attempts = 1
        var rateLimitRetried = false
        while (true) {
            val result = attempt()
            if (result !is DataResult.Failure) return result
            val wait = waitBeforeNext(result.failure, attempts, rateLimitRetried) ?: return result
            if (result.failure is ApiFailure.RateLimited) rateLimitRetried = true
            delay(wait)
            attempts++
        }
    }

    /** The milliseconds to wait before the next attempt, or `null` when the sequence ends here. */
    private fun waitBeforeNext(
        failure: ApiFailure,
        attempts: Int,
        rateLimitRetried: Boolean,
    ): Long? {
        if (attempts >= MAX_ATTEMPTS) return null
        return when (failure) {
            ApiFailure.Offline, ApiFailure.Timeout, is ApiFailure.Server -> backoff(attempts)
            is ApiFailure.RateLimited ->
                failure.retryAfterSeconds
                    ?.takeIf { !rateLimitRetried && it <= MAX_ADVISED_SECONDS }
                    ?.let { it * MILLIS_PER_SECOND }
            is ApiFailure.NotFound,
            is ApiFailure.InvalidRequest,
            is ApiFailure.GraphQl,
            ApiFailure.MalformedResponse,
            ApiFailure.EmptyBody,
            is ApiFailure.Unknown,
            -> null
        }
    }

    private fun backoff(attempts: Int): Long {
        val base = BASE_BACKOFF_MILLIS[attempts - 1]
        val factor = MIN_JITTER + (MAX_JITTER - MIN_JITTER) * random.nextDouble()
        return (base * factor).roundToLong()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val MAX_ADVISED_SECONDS = 60L
        const val MILLIS_PER_SECOND = 1_000L
        const val MIN_JITTER = 0.8
        const val MAX_JITTER = 1.2
        val BASE_BACKOFF_MILLIS = listOf(500.0, 1_500.0)
    }
}

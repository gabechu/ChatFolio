package com.chatfolio.data.network

import kotlinx.coroutines.delay

/**
 * A generic higher-order suspend function that automatically retries a block of code upon failure.
 *
 * @param times The maximum number of retry attempts before throwing the final exception.
 * @param initialDelay The initial delay in milliseconds before the first retry.
 * @param factor The multiplier to apply to the delay after each retry (exponential backoff).
 * @param shouldRetry A lambda to conditionally decide if a specific exception warrants a retry.
 * @param block The suspend network or database operation to execute.
 */
suspend fun <T> withRetry(
    times: Int = 2,
    initialDelay: Long = 2000L,
    factor: Double = 2.0,
    shouldRetry: (Exception) -> Boolean = { true },
    block: suspend () -> T,
): T {
    var currentDelay = initialDelay
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            attempt++
            if (attempt > times || !shouldRetry(e)) {
                throw e
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }
}

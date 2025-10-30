package org.dydlakcloud.resticopia.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Test extensions for common testing operations.
 */

/**
 * Creates a test scope with an optional test dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun createTestScope(
    scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    dispatcher: TestDispatcher = UnconfinedTestDispatcher(scheduler)
): TestScope = TestScope(dispatcher)

/**
 * Blocks until the CompletableFuture completes and returns the result.
 * Useful for testing CompletableFuture-based APIs.
 */
fun <T> CompletableFuture<T>.getOrThrow(timeout: Duration = 5.seconds): T {
    return try {
        this.get(timeout.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS)
    } catch (e: java.util.concurrent.ExecutionException) {
        throw e.cause ?: e
    }
}

/**
 * Returns true if the CompletableFuture completed successfully.
 */
fun <T> CompletableFuture<T>.isSuccess(): Boolean {
    return isDone && !isCompletedExceptionally
}

/**
 * Returns true if the CompletableFuture completed exceptionally.
 */
fun <T> CompletableFuture<T>.isFailure(): Boolean {
    return isCompletedExceptionally
}

/**
 * Gets the exception from a failed CompletableFuture.
 */
fun <T> CompletableFuture<T>.getException(): Throwable? {
    return try {
        get()
        null
    } catch (e: java.util.concurrent.ExecutionException) {
        e.cause
    } catch (e: Exception) {
        e
    }
}


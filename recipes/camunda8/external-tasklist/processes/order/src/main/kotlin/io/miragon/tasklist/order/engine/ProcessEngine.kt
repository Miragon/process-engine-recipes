package io.miragon.tasklist.order.engine

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/** The process engine refused a command, or did not answer it in time. */
class ProcessEngineRefused(cause: Throwable) : RuntimeException(cause.message, cause)

/** No process instance waits for the message with this correlation key. */
class NoMessageSubscription(messageName: String, correlationKey: String) :
    RuntimeException("No process instance waits for $messageName with the key $correlationKey.")

/**
 * Waits for a command of the Process Engine API and throws its failure unwrapped, so a `@Transactional` caller rolls
 * back. Never use `get()`: its checked `ExecutionException` would let Spring commit.
 */
fun <T> CompletableFuture<T>.await(): T =
    try {
        join()
    } catch (e: CompletionException) {
        throw e.cause as? RuntimeException ?: e
    }

package io.miragon.tasklist.order.orders

import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.Correlation
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import io.miragon.tasklist.order.engine.NoMessageSubscription
import io.miragon.tasklist.order.engine.await

/** Names from `order.bpmn` that the code refers to. */
object OrderProcess {
    const val ID = "order-process"
    const val ORDER_CANCELED = "OrderCanceled"
    const val PAYMENT_RECEIVED = "PaymentReceived"
    const val PAYMENT_REJECTED = "PaymentRejected"
}

/**
 * Correlates a message to the order process that waits for it with the order number as correlation key.
 *
 * Unlike publishing, correlating tells right away whether an instance was waiting; if none was, this returns false.
 * The application never sends messages to the processes of other applications.
 */
fun CorrelationApi.correlateToOrder(messageName: String, orderId: String, variables: Map<String, Any?> = emptyMap()): Boolean =
    try {
        correlateMessage(CorrelateMessageCmd(messageName, variables, Correlation.withKey(orderId))).await()
        true
    } catch (e: NoMessageSubscription) {
        false
    }

package io.miragon.tasklist.order.orders

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientHttpException

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
 * Unlike publishing, correlating tells right away whether an instance was waiting: Camunda answers 404 if none
 * was, and this returns false. The application never sends messages to the processes of other applications.
 */
fun CamundaClient.correlateToOrder(messageName: String, orderId: String, variables: Map<String, Any?> = emptyMap()): Boolean =
    try {
        newCorrelateMessageCommand()
            .messageName(messageName)
            .correlationKey(orderId)
            .variables(variables)
            .send()
            .join()
        true
    } catch (e: ClientHttpException) {
        if (e.code() == 404) false else throw e
    }

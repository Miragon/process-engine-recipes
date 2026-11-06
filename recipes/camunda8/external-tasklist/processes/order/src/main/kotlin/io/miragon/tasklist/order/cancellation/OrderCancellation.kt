package io.miragon.tasklist.order.cancellation

import io.camunda.client.CamundaClient
import io.miragon.tasklist.contract.DomainConflict
import io.miragon.tasklist.order.orders.OrderNotFound
import io.miragon.tasklist.order.orders.OrderProcess
import io.miragon.tasklist.order.orders.OrderRepository
import io.miragon.tasklist.order.orders.OrderStatus
import io.miragon.tasklist.order.orders.correlateToOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * A customer cancels an order. That works only while sales checks it: the message interrupts the open task
 * "Check order", which the tasklist then shows as canceled.
 */
@Service
class OrderCancellation(
    private val orders: OrderRepository,
    private val camunda: CamundaClient,
) {

    /** Cancels the order, or does nothing if it is canceled already, so the customer may safely ask twice. */
    @Transactional
    fun cancel(orderId: String, reason: String?) {
        val order = orders.lock(orderId) ?: throw OrderNotFound(orderId)
        if (order.status == OrderStatus.CANCELED) return

        val cancelReason = reason?.trim()?.ifEmpty { null }
        orders.recordCancellation(order.id, cancelReason)
        if (!camunda.correlateToOrder(OrderProcess.ORDER_CANCELED, order.id, mapOf("cancelReason" to cancelReason))) {
            throw DomainConflict("The order can no longer be canceled.")
        }
    }
}

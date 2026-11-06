package io.miragon.tasklist.order.orders

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import org.springframework.stereotype.Component

/**
 * The steps of the process that record its outcome on the order.
 *
 * Where a call of the application already stored the outcome while it completed a task or correlated a message
 * (decline, cancellation, payment result), the step stores it again. That keeps the order right even if the
 * call's transaction failed after Camunda had accepted the command.
 */
@Component
class OrderStatusWorkers(private val orders: OrderRepository) {

    @JobWorker(type = "order.decline")
    fun decline(@Variable orderId: String) = orders.updateStatus(orderId, OrderStatus.DECLINED)

    @JobWorker(type = "order.mark-canceled")
    fun markCanceled(@Variable orderId: String) = orders.updateStatus(orderId, OrderStatus.CANCELED)

    @JobWorker(type = "order.cancel-unpaid")
    fun cancelUnpaid(@Variable orderId: String) = orders.updateStatus(orderId, OrderStatus.NOT_PAID)

    @JobWorker(type = "order.ship")
    fun ship(@Variable orderId: String) = orders.updateStatus(orderId, OrderStatus.SHIPPED)
}

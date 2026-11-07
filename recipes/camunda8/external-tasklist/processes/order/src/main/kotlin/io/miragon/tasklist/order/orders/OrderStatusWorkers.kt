package io.miragon.tasklist.order.orders

import io.miragon.tasklist.order.engine.ServiceTask
import io.miragon.tasklist.order.engine.ServiceTaskWorkers
import org.springframework.stereotype.Component

/**
 * The steps of the process that record its outcome on the order.
 *
 * Where a call of the application already stored the outcome while it completed a task or correlated a message
 * (decline, cancellation, payment result), the step stores it again. That keeps the order right even if the
 * call's transaction failed after Camunda had accepted the command.
 */
@Component
class OrderStatusWorkers(private val orders: OrderRepository, workers: ServiceTaskWorkers) {

    init {
        workers.register("order.decline", setOf("orderId")) { record(it, OrderStatus.DECLINED) }
        workers.register("order.mark-canceled", setOf("orderId")) { record(it, OrderStatus.CANCELED) }
        workers.register("order.cancel-unpaid", setOf("orderId")) { record(it, OrderStatus.NOT_PAID) }
        workers.register("order.ship", setOf("orderId")) { record(it, OrderStatus.SHIPPED) }
    }

    private fun record(job: ServiceTask, status: OrderStatus): Map<String, Any?> {
        orders.updateStatus(job.string("orderId"), status)
        return emptyMap()
    }
}

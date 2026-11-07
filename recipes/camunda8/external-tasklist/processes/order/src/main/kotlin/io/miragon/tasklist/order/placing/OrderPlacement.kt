package io.miragon.tasklist.order.placing

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import io.miragon.tasklist.contract.ValidationFailed
import io.miragon.tasklist.order.catalog.Catalog
import io.miragon.tasklist.order.engine.await
import io.miragon.tasklist.order.orders.CURRENCY
import io.miragon.tasklist.order.orders.Customer
import io.miragon.tasklist.order.orders.NewLine
import io.miragon.tasklist.order.orders.OrderProcess
import io.miragon.tasklist.order.orders.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class PlacedOrder(val orderId: String, val processInstanceKey: Long)

@Service
class OrderPlacement(
    private val catalog: Catalog,
    private val orders: OrderRepository,
    private val processes: StartProcessApi,
) {

    /**
     * Stores the order and starts its process in one transaction, so no order remains if Camunda refuses the start.
     * The order number becomes the business ID of the process instance; the tasklist shows the `subject`.
     */
    @Transactional
    fun place(order: NewOrder): PlacedOrder {
        val items = catalog.items().associateBy { it.id }
        val errors = order.errors(items)
        if (errors.isNotEmpty()) throw ValidationFailed("The order is not complete. Check the marked fields.", errors)

        val customer = order.customer.let { Customer(it.name.trim(), it.email.trim(), it.address.trim()) }
        val lines = order.items.map { NewLine(it.itemId, it.quantity, items.getValue(it.itemId).price) }
        val total = lines.sumOf { it.price * it.quantity.toBigDecimal() }
        val orderId = orders.insert(customer, lines, total)

        val subject = "Order $orderId · ${customer.name} · ${total.toPlainString()} $CURRENCY"
        val instance = processes.startProcess(
            StartProcessByDefinitionCmd(
                OrderProcess.ID,
                mapOf(CommonRestrictions.BUSINESS_KEY to orderId, "orderId" to orderId, "subject" to subject),
            ),
        ).await()
        val processInstanceKey = instance.instanceId.toLong()
        orders.recordProcessInstance(orderId, processInstanceKey)
        return PlacedOrder(orderId, processInstanceKey)
    }
}

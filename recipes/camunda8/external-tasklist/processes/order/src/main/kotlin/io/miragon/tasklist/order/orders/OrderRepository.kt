package io.miragon.tasklist.order.orders

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime

/** A line of a new order, priced from the catalog. */
data class NewLine(val itemId: String, val quantity: Int, val price: BigDecimal)

@Repository
class OrderRepository(private val jdbc: JdbcClient) {

    /** Stores a new order in status [OrderStatus.CHECKING] and returns its number, for example `ORD-1001`. */
    fun insert(customer: Customer, lines: List<NewLine>, total: BigDecimal): String {
        val orderId = jdbc.sql(
            """
            INSERT INTO orders (status, customer_name, customer_email, customer_address, total)
            VALUES ('CHECKING', :name, :email, :address, :total)
            RETURNING id
            """,
        )
            .param("name", customer.name)
            .param("email", customer.email)
            .param("address", customer.address)
            .param("total", total)
            .query(String::class.java)
            .single()
        lines.forEach { line ->
            jdbc.sql("INSERT INTO order_items (order_id, item_id, quantity, price) VALUES (:orderId, :itemId, :quantity, :price)")
                .param("orderId", orderId)
                .param("itemId", line.itemId)
                .param("quantity", line.quantity)
                .param("price", line.price)
                .update()
        }
        return orderId
    }

    fun find(orderId: String): Order? = load(orderId, lock = false)

    /** Loads the order and locks it until the transaction ends, so calls for the same order run one after the other. */
    fun lock(orderId: String): Order? = load(orderId, lock = true)

    fun recordProcessInstance(orderId: String, processInstanceKey: Long) =
        update(orderId, "process_instance_key = :key", "key" to processInstanceKey)

    fun recordCheck(orderId: String, status: OrderStatus, comment: String?) =
        update(orderId, "status = :status, check_comment = :comment", "status" to status.name, "comment" to comment)

    fun recordPaymentRequest(orderId: String, paymentId: String) =
        update(orderId, "status = 'AWAITING_PAYMENT', payment_id = :paymentId", "paymentId" to paymentId)

    fun recordPaymentResult(orderId: String, paymentId: String, result: PaymentStatus, reason: String?, status: OrderStatus) =
        update(
            orderId,
            "status = :status, payment_id = :paymentId, payment_status = :result, payment_reason = :reason",
            "status" to status.name, "paymentId" to paymentId, "result" to result.name, "reason" to reason,
        )

    fun recordCancellation(orderId: String, reason: String?) =
        update(orderId, "status = 'CANCELED', cancel_reason = :reason", "reason" to reason)

    fun recordDelivery(orderId: String, readyItems: Set<String>, shippingDate: LocalDate?, note: String?) {
        jdbc.sql("UPDATE order_items SET ready = FALSE WHERE order_id = :orderId")
            .param("orderId", orderId)
            .update()
        if (readyItems.isNotEmpty()) {
            jdbc.sql("UPDATE order_items SET ready = TRUE WHERE order_id = :orderId AND item_id IN (:readyItems)")
                .param("orderId", orderId)
                .param("readyItems", readyItems)
                .update()
        }
        update(orderId, "shipping_date = :date, delivery_note = :note", "date" to shippingDate, "note" to note)
    }

    fun updateStatus(orderId: String, status: OrderStatus) =
        update(orderId, "status = :status", "status" to status.name)

    private fun update(orderId: String, assignments: String, vararg params: Pair<String, Any?>) {
        jdbc.sql("UPDATE orders SET $assignments, updated_at = now() WHERE id = :orderId")
            .param("orderId", orderId)
            .apply { params.forEach { (name, value) -> param(name, value) } }
            .update()
    }

    private fun load(orderId: String, lock: Boolean): Order? {
        val order = jdbc.sql("SELECT * FROM orders WHERE id = :orderId" + if (lock) " FOR UPDATE" else "")
            .param("orderId", orderId)
            .query { rs, _ -> rs.toOrder() }
            .optional()
            .orElse(null) ?: return null
        val lines = jdbc.sql(
            """
            SELECT oi.item_id, i.name, oi.quantity, oi.price, oi.ready
            FROM order_items oi JOIN items i ON i.id = oi.item_id
            WHERE oi.order_id = :orderId
            ORDER BY i.name
            """,
        )
            .param("orderId", orderId)
            .query { rs, _ ->
                OrderLine(
                    itemId = rs.getString("item_id"),
                    name = rs.getString("name"),
                    quantity = rs.getInt("quantity"),
                    price = rs.getBigDecimal("price"),
                    ready = rs.getBoolean("ready"),
                )
            }
            .list()
        return order.copy(lines = lines)
    }

    private fun ResultSet.toOrder() = Order(
        id = getString("id"),
        status = OrderStatus.valueOf(getString("status")),
        customer = Customer(getString("customer_name"), getString("customer_email"), getString("customer_address")),
        lines = emptyList(),
        total = getBigDecimal("total"),
        checkComment = getString("check_comment"),
        cancelReason = getString("cancel_reason"),
        paymentId = getString("payment_id"),
        paymentStatus = getString("payment_status")?.let(PaymentStatus::valueOf),
        paymentReason = getString("payment_reason"),
        shippingDate = getObject("shipping_date", LocalDate::class.java),
        deliveryNote = getString("delivery_note"),
        processInstanceKey = getObject("process_instance_key", Long::class.javaObjectType),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    )
}

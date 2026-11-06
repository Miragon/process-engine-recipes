package io.miragon.tasklist.payment

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.jvm.optionals.getOrNull

data class NewPayment(
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val customer: Customer,
    val callbackUrl: String,
)

@Repository
class PaymentRepository(private val jdbc: JdbcClient) {

    fun find(id: String): Payment? =
        jdbc.sql("SELECT * FROM payments WHERE id = :id")
            .param("id", id)
            .query(rows).optional().getOrNull()

    fun findByOrderId(orderId: String): Payment? =
        jdbc.sql("SELECT * FROM payments WHERE order_id = :orderId")
            .param("orderId", orderId)
            .query(rows).optional().getOrNull()

    /**
     * Inserts the payment and returns it with its new ID, or `null` if the order already has a payment.
     * A concurrent insert of the same order waits for the other transaction and then inserts nothing.
     */
    fun insert(payment: NewPayment): Payment? =
        jdbc.sql(
            """
            INSERT INTO payments (order_id, amount, currency, customer_name, customer_email, callback_url)
            VALUES (:orderId, :amount, :currency, :customerName, :customerEmail, :callbackUrl)
            ON CONFLICT (order_id) DO NOTHING
            RETURNING *
            """,
        )
            .param("orderId", payment.orderId)
            .param("amount", payment.amount)
            .param("currency", payment.currency)
            .param("customerName", payment.customer.name)
            .param("customerEmail", payment.customer.email)
            .param("callbackUrl", payment.callbackUrl)
            .query(rows).optional().getOrNull()

    fun linkProcessInstance(id: String, processInstanceKey: Long) {
        jdbc.sql("UPDATE payments SET process_instance_key = :key WHERE id = :id")
            .param("key", processInstanceKey)
            .param("id", id)
            .update()
    }

    /** Records the decision if the payment is still open, and tells whether it did. */
    fun decide(id: String, decision: PaymentDecision): Boolean =
        jdbc.sql(
            """
            UPDATE payments
            SET status = :status, amount_received = :amountReceived, rejection_reason = :rejectionReason, decided_at = now()
            WHERE id = :id AND status = 'OPEN'
            """,
        )
            .param("status", decision.status.name)
            .param("amountReceived", decision.amountReceived)
            .param("rejectionReason", decision.rejectionReason)
            .param("id", id)
            .update() == 1

    fun countReminder(id: String) {
        jdbc.sql("UPDATE payments SET reminders_sent = reminders_sent + 1 WHERE id = :id").param("id", id).update()
    }

    fun markReported(id: String) {
        jdbc.sql("UPDATE payments SET reported_at = now() WHERE id = :id").param("id", id).update()
    }

    private companion object {
        val rows = RowMapper { rs, _ ->
            Payment(
                id = rs.getString("id"),
                orderId = rs.getString("order_id"),
                amount = rs.getBigDecimal("amount"),
                currency = rs.getString("currency"),
                customer = Customer(rs.getString("customer_name"), rs.getString("customer_email")),
                callbackUrl = rs.getString("callback_url"),
                status = PaymentStatus.valueOf(rs.getString("status")),
                rejectionReason = rs.getString("rejection_reason"),
                amountReceived = rs.getBigDecimal("amount_received"),
                remindersSent = rs.getInt("reminders_sent"),
                processInstanceKey = rs.getObject("process_instance_key") as Long?,
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                decidedAt = rs.getObject("decided_at", OffsetDateTime::class.java)?.toInstant(),
                reportedAt = rs.getObject("reported_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }
    }
}

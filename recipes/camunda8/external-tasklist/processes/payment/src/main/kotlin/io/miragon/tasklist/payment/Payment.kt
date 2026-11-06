package io.miragon.tasklist.payment

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

data class Payment(
    val id: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val customer: Customer,
    val callbackUrl: String,
    val status: PaymentStatus,
    val rejectionReason: String?,
    val amountReceived: BigDecimal?,
    val remindersSent: Int,
    val processInstanceKey: Long?,
    val createdAt: Instant,
    val decidedAt: Instant?,
    val reportedAt: Instant?,
) {
    /** The amount as an invoice shows it, for example `129.90 EUR`. */
    val invoiceAmount: String
        get() = "${amount.setScale(2, RoundingMode.HALF_UP).toPlainString()} $currency"
}

data class Customer(val name: String, val email: String)

enum class PaymentStatus { OPEN, PAID, REJECTED }

/** What the finance team, or the overdue timer, decided about an open payment. */
data class PaymentDecision(
    val status: PaymentStatus,
    val amountReceived: BigDecimal? = null,
    val rejectionReason: String? = null,
) {
    companion object {
        const val OVERDUE = "Payment overdue"

        fun paid(amountReceived: BigDecimal) = PaymentDecision(PaymentStatus.PAID, amountReceived = amountReceived)

        fun rejected(reason: String) = PaymentDecision(PaymentStatus.REJECTED, rejectionReason = reason)
    }
}

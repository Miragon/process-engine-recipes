package io.miragon.tasklist.payment.deadline

import io.miragon.tasklist.payment.PaymentDecision
import io.miragon.tasklist.payment.PaymentRepository
import io.miragon.tasklist.payment.engine.ServiceTaskWorkers
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** The two timers on "Check payment": a reminder while the payment is open, and the rejection once it is overdue. */
@Component
class PaymentDeadlineWorkers(private val payments: PaymentRepository, workers: ServiceTaskWorkers) {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        workers.register("payment.send-reminder", setOf("paymentId")) { sendReminder(it.string("paymentId")); emptyMap() }
        workers.register("payment.mark-overdue", setOf("paymentId")) { markOverdue(it.string("paymentId")) }
    }

    private fun sendReminder(paymentId: String) {
        val payment = checkNotNull(payments.find(paymentId)) { "There is no payment $paymentId." }
        log.info(
            "Payment {} is still open, would send a reminder to {} to pay {} for order {}",
            payment.id, payment.customer.email, payment.invoiceAmount, payment.orderId,
        )
        payments.countReminder(payment.id)
    }

    /**
     * Rejects the payment unless it was decided in the meantime, and returns the stored result either way,
     * so a retried job or a decision that won the race reports the truth.
     */
    private fun markOverdue(paymentId: String): Map<String, Any?> {
        payments.decide(paymentId, PaymentDecision.rejected(PaymentDecision.OVERDUE))
        val payment = checkNotNull(payments.find(paymentId)) { "There is no payment $paymentId." }
        return mapOf("paymentStatus" to payment.status.name, "rejectionReason" to payment.rejectionReason)
    }
}

package io.miragon.tasklist.payment.check

import io.miragon.tasklist.contract.ValidationFailed
import io.miragon.tasklist.payment.Payment
import io.miragon.tasklist.payment.PaymentDecision
import java.math.BigDecimal

/** The only fields the form may change; the provider API drops everything else. */
data class CheckPaymentSubmission(
    val paymentReceived: Boolean? = null,
    val amountReceived: BigDecimal? = null,
    val rejectionReason: String? = null,
)

/**
 * Turns what the finance team entered into a decision, or names the field that is wrong.
 * Only the field that belongs to the answer counts: the amount for a received payment, the reason for a rejection.
 */
fun decide(payment: Payment, submission: CheckPaymentSubmission): PaymentDecision =
    when (submission.paymentReceived) {
        null -> throw ValidationFailed("paymentReceived", "State whether the payment was received.")
        true -> {
            val received = submission.amountReceived
            if (received == null || received.compareTo(payment.amount) != 0) {
                throw ValidationFailed("amountReceived", "The received amount must match the invoice amount of ${payment.invoiceAmount}")
            }
            PaymentDecision.paid(received)
        }
        false -> {
            val reason = submission.rejectionReason?.trim()
            if (reason.isNullOrEmpty()) throw ValidationFailed("rejectionReason", "Give a reason for rejecting the payment.")
            if (reason.length > MAX_REASON_LENGTH) {
                throw ValidationFailed("rejectionReason", "Keep the reason within $MAX_REASON_LENGTH characters.")
            }
            PaymentDecision.rejected(reason)
        }
    }

/** Matches `maxLength` of `rejectionReason` in `forms/check-payment@1.json`. */
const val MAX_REASON_LENGTH = 500

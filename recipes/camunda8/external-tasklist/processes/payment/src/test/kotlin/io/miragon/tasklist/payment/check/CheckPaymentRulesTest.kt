package io.miragon.tasklist.payment.check

import io.miragon.tasklist.contract.FieldError
import io.miragon.tasklist.contract.ValidationFailed
import io.miragon.tasklist.payment.PaymentDecision
import io.miragon.tasklist.payment.aPayment
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CheckPaymentRulesTest {

    private val payment = aPayment()

    @Test
    fun `asks whether the payment was received`() {
        assertRefused(CheckPaymentSubmission(), FieldError("paymentReceived", "State whether the payment was received."))
    }

    @Test
    fun `accepts a received payment of the invoice amount and ignores the reason`() {
        val submission = CheckPaymentSubmission(paymentReceived = true, amountReceived = BigDecimal("129.9"), rejectionReason = "typo")

        assertThat(decide(payment, submission)).isEqualTo(PaymentDecision.paid(BigDecimal("129.9")))
    }

    @Test
    fun `refuses a received amount that differs from the invoice or is missing`() {
        val mismatch = FieldError("amountReceived", "The received amount must match the invoice amount of 129.90 EUR")

        assertRefused(CheckPaymentSubmission(paymentReceived = true, amountReceived = BigDecimal("129.00")), mismatch)
        assertRefused(CheckPaymentSubmission(paymentReceived = true), mismatch)
    }

    @Test
    fun `rejects with the given reason and ignores the amount`() {
        val submission = CheckPaymentSubmission(paymentReceived = false, amountReceived = BigDecimal.TEN, rejectionReason = "  No money on the account  ")

        assertThat(decide(payment, submission)).isEqualTo(PaymentDecision.rejected("No money on the account"))
    }

    @Test
    fun `refuses a rejection without a reason or with a reason that is too long`() {
        assertRefused(
            CheckPaymentSubmission(paymentReceived = false, rejectionReason = " "),
            FieldError("rejectionReason", "Give a reason for rejecting the payment."),
        )
        assertRefused(
            CheckPaymentSubmission(paymentReceived = false, rejectionReason = "x".repeat(MAX_REASON_LENGTH + 1)),
            FieldError("rejectionReason", "Keep the reason within 500 characters."),
        )
    }

    private fun assertRefused(submission: CheckPaymentSubmission, error: FieldError) {
        assertThatThrownBy { decide(payment, submission) }
            .isInstanceOfSatisfying(ValidationFailed::class.java) { assertThat(it.errors).containsExactly(error) }
    }
}

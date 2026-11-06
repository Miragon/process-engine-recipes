package io.miragon.tasklist.payment.api

import io.miragon.tasklist.contract.FieldError
import io.miragon.tasklist.payment.Customer
import io.miragon.tasklist.payment.NewPayment
import io.miragon.tasklist.payment.Payment
import io.miragon.tasklist.payment.report.CallbackProperties
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

data class PaymentRequest(
    @field:Schema(description = "One payment per order", example = "ORD-1001")
    val orderId: String,
    @field:Schema(description = "Above 0, at most two decimals", example = "129.90")
    val amount: BigDecimal,
    @field:Schema(description = "ISO 4217 code", example = "EUR")
    val currency: String,
    val customer: Customer,
    @field:Schema(
        description = "Receives `POST {paymentId, status, reason}` once the payment is decided. " +
            "Its host must be one of `payment.callback.allowed-hosts`.",
        example = "http://order-app:8081/api/v1/orders/ORD-1001/payment-result",
    )
    val callbackUrl: String,
) {
    fun violations(callbacks: CallbackProperties): List<FieldError> = buildList {
        if (orderId.isBlank() || orderId.length > 64) {
            add(FieldError("orderId", "Give the order ID in at most 64 characters."))
        }
        if (amount.signum() <= 0 || amount > MAX_AMOUNT || amount.stripTrailingZeros().scale() > 2) {
            add(FieldError("amount", "Give an amount above 0 and below 100,000,000 with at most two decimals."))
        }
        if (!CURRENCY.matches(currency)) {
            add(FieldError("currency", "Give the currency as a three-letter ISO code such as EUR."))
        }
        if (customer.name.isBlank()) {
            add(FieldError("customer.name", "Give the name of the customer."))
        }
        if (!EMAIL.matches(customer.email)) {
            add(FieldError("customer.email", "Give the email address of the customer."))
        }
        if (!callbacks.allows(callbackUrl)) {
            add(FieldError("callbackUrl", "Give an http or https URL on a host this application may call."))
        }
    }

    /**
     * Decides what a request means for an order that already has a payment: the same amount again
     * is a retry of the first request, another amount contradicts it.
     */
    fun outcomeFor(existing: Payment): RequestOutcome =
        if (existing.amount.compareTo(amount) == 0 && existing.currency == currency) {
            RequestOutcome.Repeated(existing.id)
        } else {
            RequestOutcome.Conflicting(existing)
        }

    fun toNewPayment() = NewPayment(orderId, amount, currency, customer, callbackUrl)

    private companion object {
        val MAX_AMOUNT = BigDecimal("99999999.99")
        val CURRENCY = Regex("[A-Z]{3}")
        val EMAIL = Regex("""[^@\s]+@[^@\s]+""")
    }
}

sealed interface RequestOutcome {
    data class Created(val paymentId: String) : RequestOutcome

    data class Repeated(val paymentId: String) : RequestOutcome

    data class Conflicting(val existing: Payment) : RequestOutcome
}

package io.miragon.tasklist.payment.api

import io.miragon.tasklist.payment.Customer
import io.miragon.tasklist.payment.aPayment
import io.miragon.tasklist.payment.report.CallbackProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaymentRequestTest {

    private val callbacks = CallbackProperties(allowedHosts = setOf("order-app"))
    private val request = PaymentRequest(
        orderId = "ORD-1001",
        amount = BigDecimal("129.90"),
        currency = "EUR",
        customer = Customer("Alice Smith", "alice@example.com"),
        callbackUrl = "http://order-app:8081/api/v1/orders/ORD-1001/payment-result",
    )

    @Test
    fun `accepts a complete request`() {
        assertThat(request.violations(callbacks)).isEmpty()
        assertThat(request.copy(amount = BigDecimal("129.900")).violations(callbacks)).isEmpty()
    }

    @Test
    fun `names every invalid field`() {
        val invalid = request.copy(
            orderId = " ",
            amount = BigDecimal("12.345"),
            currency = "eur",
            customer = Customer("", "alice"),
            callbackUrl = "http://payment-db:5432/",
        )

        assertThat(invalid.violations(callbacks).map { it.field })
            .containsExactly("orderId", "amount", "currency", "customer.name", "customer.email", "callbackUrl")
    }

    @Test
    fun `refuses amounts that are not positive or do not fit the invoice`() {
        listOf("0", "-1", "100000000").forEach {
            assertThat(request.copy(amount = BigDecimal(it)).violations(callbacks).map { error -> error.field }).containsExactly("amount")
        }
    }

    @Test
    fun `treats the same order and amount as a repeated request`() {
        assertThat(request.copy(amount = BigDecimal("129.9")).outcomeFor(aPayment())).isEqualTo(RequestOutcome.Repeated("PAY-1001"))
    }

    @Test
    fun `treats another amount or currency for the same order as a conflict`() {
        val existing = aPayment()

        assertThat(request.copy(amount = BigDecimal("99.00")).outcomeFor(existing)).isEqualTo(RequestOutcome.Conflicting(existing))
        assertThat(request.copy(currency = "USD").outcomeFor(existing)).isEqualTo(RequestOutcome.Conflicting(existing))
    }
}

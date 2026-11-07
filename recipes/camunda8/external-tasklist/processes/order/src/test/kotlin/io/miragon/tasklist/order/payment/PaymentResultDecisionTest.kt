package io.miragon.tasklist.order.payment

import io.miragon.tasklist.order.orders.Customer
import io.miragon.tasklist.order.orders.Order
import io.miragon.tasklist.order.orders.OrderStatus
import io.miragon.tasklist.order.orders.PaymentStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime

class PaymentResultDecisionTest {

    private val awaitingPayment = Order(
        id = "ORD-1001",
        status = OrderStatus.AWAITING_PAYMENT,
        customer = Customer("Alice Smith", "alice@example.com", "Hauptstraße 1"),
        lines = emptyList(),
        total = BigDecimal("129.90"),
        checkComment = null,
        cancelReason = null,
        paymentId = "PAY-1",
        paymentStatus = null,
        paymentReason = null,
        shippingDate = null,
        deliveryNote = null,
        processInstanceKey = 2251799813685328,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
    )
    private val paid = PaymentResult("PAY-1", PaymentStatus.PAID)

    @Test
    fun `applies the first result of the requested payment`() {
        assertThat(decide(awaitingPayment, paid)).isEqualTo(PaymentResultDecision.Apply)
    }

    @Test
    fun `applies a result that arrives before the payment id was stored`() {
        assertThat(decide(awaitingPayment.copy(paymentId = null), paid)).isEqualTo(PaymentResultDecision.Apply)
    }

    @Test
    fun `accepts the same result again without applying it twice`() {
        val paidOrder = awaitingPayment.copy(status = OrderStatus.SHIPPED, paymentStatus = PaymentStatus.PAID)

        assertThat(decide(paidOrder, paid)).isEqualTo(PaymentResultDecision.AlreadyApplied)
    }

    @Test
    fun `rejects a result that contradicts an earlier one`() {
        val paidOrder = awaitingPayment.copy(status = OrderStatus.PREPARING, paymentStatus = PaymentStatus.PAID)

        assertThat(decide(paidOrder, paid.copy(status = PaymentStatus.REJECTED)))
            .isInstanceOf(PaymentResultDecision.Contradiction::class.java)
        assertThat(decide(paidOrder, paid.copy(paymentId = "PAY-2")))
            .isInstanceOf(PaymentResultDecision.Contradiction::class.java)
    }

    @Test
    fun `rejects a result for another payment or for an order that waits for none`() {
        assertThat(decide(awaitingPayment, paid.copy(paymentId = "PAY-2")))
            .isEqualTo(PaymentResultDecision.Contradiction("Order ORD-1001 waits for payment PAY-1, not PAY-2."))
        assertThat(decide(awaitingPayment.copy(status = OrderStatus.DECLINED, paymentId = null), paid))
            .isEqualTo(PaymentResultDecision.Contradiction("Order ORD-1001 does not wait for a payment, it is DECLINED."))
    }
}

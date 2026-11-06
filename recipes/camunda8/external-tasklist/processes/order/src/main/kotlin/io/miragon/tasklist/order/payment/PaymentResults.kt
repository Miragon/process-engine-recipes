package io.miragon.tasklist.order.payment

import io.camunda.client.CamundaClient
import io.miragon.tasklist.contract.DomainConflict
import io.miragon.tasklist.order.orders.Order
import io.miragon.tasklist.order.orders.OrderNotFound
import io.miragon.tasklist.order.orders.OrderProcess
import io.miragon.tasklist.order.orders.OrderRepository
import io.miragon.tasklist.order.orders.OrderStatus
import io.miragon.tasklist.order.orders.PaymentStatus
import io.miragon.tasklist.order.orders.correlateToOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** What the payment application reports to `POST /api/v1/orders/{orderId}/payment-result`. */
data class PaymentResult(
    val paymentId: String,
    val status: PaymentStatus,
    val reason: String? = null,
)

/** The order process does not wait for a payment result yet; the payment application should try again later. */
class ProcessNotWaiting(message: String) : RuntimeException(message)

sealed interface PaymentResultDecision {
    data object Apply : PaymentResultDecision

    /** The same result arrived before; the payment application repeats it, for example after a timeout. */
    data object AlreadyApplied : PaymentResultDecision

    data class Contradiction(val reason: String) : PaymentResultDecision
}

/** Decides what a reported result means for the order, so the payment application may safely report it again. */
fun decide(order: Order, result: PaymentResult): PaymentResultDecision = when {
    order.paymentStatus == result.status && order.paymentId == result.paymentId -> PaymentResultDecision.AlreadyApplied
    order.paymentStatus != null ->
        PaymentResultDecision.Contradiction("Payment ${order.paymentId} of order ${order.id} was already reported as ${order.paymentStatus}.")
    order.paymentId != null && order.paymentId != result.paymentId ->
        PaymentResultDecision.Contradiction("Order ${order.id} waits for payment ${order.paymentId}, not ${result.paymentId}.")
    order.status != OrderStatus.CHECKING && order.status != OrderStatus.AWAITING_PAYMENT ->
        PaymentResultDecision.Contradiction("Order ${order.id} does not wait for a payment, it is ${order.status}.")
    else -> PaymentResultDecision.Apply
}

@Service
class PaymentResults(
    private val orders: OrderRepository,
    private val camunda: CamundaClient,
) {

    /**
     * Stores the result and passes it to the waiting order process in one transaction. If the process does not
     * wait yet, Camunda answers 404 and nothing is stored, so the payment application can simply report again.
     */
    @Transactional
    fun apply(orderId: String, result: PaymentResult) {
        val order = orders.lock(orderId) ?: throw OrderNotFound(orderId)
        when (val decision = decide(order, result)) {
            PaymentResultDecision.AlreadyApplied -> return
            is PaymentResultDecision.Contradiction -> throw DomainConflict(decision.reason)
            PaymentResultDecision.Apply -> {
                val (message, status) = when (result.status) {
                    PaymentStatus.PAID -> OrderProcess.PAYMENT_RECEIVED to OrderStatus.PREPARING
                    PaymentStatus.REJECTED -> OrderProcess.PAYMENT_REJECTED to OrderStatus.NOT_PAID
                }
                orders.recordPaymentResult(order.id, result.paymentId, result.status, result.reason, status)
                val variables = mapOf("paymentId" to result.paymentId, "paymentReason" to result.reason)
                if (!camunda.correlateToOrder(message, order.id, variables)) {
                    throw ProcessNotWaiting("Order ${order.id} does not wait for a payment result yet. Report it again later.")
                }
            }
        }
    }
}

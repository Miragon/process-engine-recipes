package io.miragon.tasklist.order.payment

import io.miragon.tasklist.order.OrderProperties
import io.miragon.tasklist.order.engine.IncidentRequired
import io.miragon.tasklist.order.engine.RetryLater
import io.miragon.tasklist.order.engine.ServiceTask
import io.miragon.tasklist.order.engine.ServiceTaskWorkers
import io.miragon.tasklist.order.orders.CURRENCY
import io.miragon.tasklist.order.orders.OrderRepository
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * The step "Request payment": asks the payment application to collect the order's total.
 * The payment application later reports the result to `POST /api/v1/orders/{orderId}/payment-result`.
 */
@Component
class RequestPaymentWorker(
    private val orders: OrderRepository,
    private val payments: PaymentsClient,
    private val properties: OrderProperties,
    workers: ServiceTaskWorkers,
) {

    init {
        workers.register("order.request-payment", setOf("orderId"), ::requestPayment)
    }

    private fun requestPayment(job: ServiceTask): Map<String, Any?> {
        val orderId = job.string("orderId")
        val order = orders.find(orderId) ?: throw IncidentRequired("There is no order $orderId.")
        val request = PaymentRequest(
            orderId = order.id,
            amount = order.total,
            currency = CURRENCY,
            customer = PaymentRequest.Payer(order.customer.name, order.customer.email),
            callbackUrl = "${properties.callbackBaseUrl.trimEnd('/')}/api/v1/orders/${order.id}/payment-result",
        )
        return when (val outcome = payments.requestPayment(request)) {
            is PaymentRequestOutcome.Accepted -> {
                orders.recordPaymentRequest(order.id, outcome.paymentId)
                mapOf("paymentId" to outcome.paymentId)
            }
            // No retries left: the engine raises an incident with the reason for a human to look at.
            is PaymentRequestOutcome.Refused -> throw IncidentRequired(outcome.reason)
            // One retry less, and a longer wait each time.
            is PaymentRequestOutcome.Unavailable -> throw RetryLater(outcome.reason, retryBackoff(job.retries - 1))
        }
    }
}

/**
 * Exponential backoff that waits longer the fewer retries are left: 16 s before the last retry, 8 s before the one
 * before it, down to 2 s. A short outage costs little, a long one does not hammer the payment application.
 */
internal fun retryBackoff(retriesLeft: Int): Duration = Duration.ofSeconds(16L shr (retriesLeft - 1).coerceIn(0, 3))

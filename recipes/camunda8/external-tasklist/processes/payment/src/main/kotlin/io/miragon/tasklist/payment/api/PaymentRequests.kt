package io.miragon.tasklist.payment.api

import io.miragon.tasklist.payment.PaymentProcess
import io.miragon.tasklist.payment.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentRequests(
    private val payments: PaymentRepository,
    private val process: PaymentProcess,
) {
    /**
     * Stores a new payment and starts its process in one transaction. The payment is committed only
     * if Camunda accepted the start, so a failed start leaves no payment without a process behind.
     */
    @Transactional
    fun request(request: PaymentRequest): RequestOutcome {
        payments.findByOrderId(request.orderId)?.let { return request.outcomeFor(it) }
        val payment = payments.insert(request.toNewPayment())
            // A concurrent request for the same order committed first.
            ?: return request.outcomeFor(checkNotNull(payments.findByOrderId(request.orderId)))
        payments.linkProcessInstance(payment.id, process.start(payment))
        return RequestOutcome.Created(payment.id)
    }
}

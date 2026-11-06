package io.miragon.tasklist.payment.check

import io.camunda.client.CamundaClient
import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.DomainConflict
import io.miragon.tasklist.contract.provider.completeUserTask
import io.miragon.tasklist.payment.Payment
import io.miragon.tasklist.payment.PaymentDecision
import io.miragon.tasklist.payment.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentDecisions(
    private val payments: PaymentRepository,
    private val camunda: CamundaClient,
) {
    /**
     * Stores the decision and completes the user task in one transaction. If Camunda refuses the completion
     * (the task is gone, or a listener denied it), the decision rolls back. If Camunda accepted it but did not
     * confirm in time (504), the decision stays: Camunda completes the task once its listeners are done.
     */
    @Transactional
    fun record(userTaskKey: String, payment: Payment, decision: PaymentDecision): CompletionOutcome {
        if (!payments.decide(payment.id, decision)) throw DomainConflict("Payment ${payment.id} was decided already.")
        return camunda.completeUserTask(
            userTaskKey,
            mapOf("paymentStatus" to decision.status.name, "rejectionReason" to decision.rejectionReason),
        )
    }
}

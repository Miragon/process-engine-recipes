package io.miragon.tasklist.payment

import io.camunda.client.CamundaClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * @property reminderAfter how long the payment may stay unchecked before the customer gets a reminder.
 * @property overdueAfter how long the payment may stay unchecked before it is rejected as overdue.
 */
@ConfigurationProperties("payment")
data class PaymentProcessProperties(
    val reminderAfter: Duration = Duration.ofDays(3),
    val overdueAfter: Duration = Duration.ofDays(14),
)

/** Starts `payment-process` (`bpmn/payment.bpmn`) for a payment. */
@Component
class PaymentProcess(
    private val camunda: CamundaClient,
    private val properties: PaymentProcessProperties,
) {
    /** Returns the key of the new process instance; throws if Camunda does not accept the start. */
    fun start(payment: Payment): Long =
        camunda.newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .businessId(payment.id)
            .variables(
                mapOf(
                    "paymentId" to payment.id,
                    "orderId" to payment.orderId,
                    "amount" to payment.amount,
                    "currency" to payment.currency,
                    "subject" to "Payment ${payment.id} · Order ${payment.orderId} · ${payment.invoiceAmount}",
                    "reminderAfter" to properties.reminderAfter.toString(),
                    "overdueAfter" to properties.overdueAfter.toString(),
                ),
            )
            .send().join()
            .processInstanceKey

    companion object {
        const val PROCESS_ID = "payment-process"
    }
}

package io.miragon.tasklist.payment.check

import io.miragon.tasklist.contract.BadTasklistRequest
import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.FormKind
import io.miragon.tasklist.contract.FormView
import io.miragon.tasklist.contract.JsonFormsView
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.TaskNotActive
import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.contract.provider.TaskFormHandler
import io.miragon.tasklist.payment.Payment
import io.miragon.tasklist.payment.PaymentRepository
import io.miragon.tasklist.payment.PaymentStatus
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

/** The form of the user task "Check payment": finance confirms that the money arrived, or rejects the payment. */
@Component
class CheckPaymentForm(
    private val payments: PaymentRepository,
    private val decisions: PaymentDecisions,
    mapper: JsonMapper,
) : TaskFormHandler<CheckPaymentSubmission> {

    override val formRef = "check-payment@1"
    override val kind = FormKind.JSON_FORMS
    override val submissionType = CheckPaymentSubmission::class.java

    /** JSON Schema and UI schema (JSON Forms 3), kept as a resource that reads like what the tasklist renders. */
    private val form: Map<String, Map<String, Any?>> =
        ClassPathResource("forms/$formRef.json").inputStream.use { mapper.readValue(it) }

    override fun view(context: TaskContext): FormView {
        val payment = paymentOf(context)
        val editable = context.state == TaskState.OPEN &&
            context.assignee == context.user.id &&
            payment.status == PaymentStatus.OPEN
        return JsonFormsView(
            formRef = formRef,
            schema = form.getValue("schema"),
            uiSchema = form.getValue("uiSchema"),
            data = formData(payment),
            readOnly = !editable,
        )
    }

    override fun complete(context: TaskContext, submission: CheckPaymentSubmission): CompletionOutcome {
        if (context.state != TaskState.OPEN) throw TaskNotActive()
        val payment = paymentOf(context)
        return decisions.record(context.userTaskKey, payment, decide(payment, submission))
    }

    /** The business ID of the task is the payment ID; the payment must also belong to the task's process instance. */
    private fun paymentOf(context: TaskContext): Payment =
        context.businessId?.let(payments::find)
            ?.takeIf { it.processInstanceKey?.toString() == context.processInstanceKey }
            ?: throw BadTasklistRequest("The task does not belong to a payment of this application.")
}

/** Leaves out empty fields, so JSON Forms shows them as not filled in rather than as invalid. */
private fun formData(payment: Payment): Map<String, Any?> =
    mapOf(
        "paymentId" to payment.id,
        "orderId" to payment.orderId,
        "customerName" to payment.customer.name,
        "customerEmail" to payment.customer.email,
        "invoiceAmount" to payment.invoiceAmount,
        "paymentReceived" to when (payment.status) {
            PaymentStatus.OPEN -> null
            PaymentStatus.PAID -> true
            PaymentStatus.REJECTED -> false
        },
        "amountReceived" to payment.amountReceived,
        "rejectionReason" to payment.rejectionReason,
    ).filterValues { it != null }

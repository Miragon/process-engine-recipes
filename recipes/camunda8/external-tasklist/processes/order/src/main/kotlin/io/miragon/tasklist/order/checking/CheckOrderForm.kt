package io.miragon.tasklist.order.checking

import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.FormKind
import io.miragon.tasklist.contract.HtmlFormView
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.TaskNotActive
import io.miragon.tasklist.contract.ValidationFailed
import io.miragon.tasklist.contract.provider.TaskFormHandler
import io.miragon.tasklist.contract.provider.completeUserTask
import io.miragon.tasklist.order.orders.CURRENCY
import io.miragon.tasklist.order.orders.Customer
import io.miragon.tasklist.order.orders.Order
import io.miragon.tasklist.order.orders.OrderRepository
import io.miragon.tasklist.order.orders.OrderStatus
import io.miragon.tasklist.order.orders.editable
import io.miragon.tasklist.order.orders.findFor
import io.miragon.tasklist.order.orders.lockFor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/** What the sales team decides; any other field of the submitted data is ignored. */
data class CheckOrderDecision(
    val approved: Boolean? = null,
    val comment: String? = null,
)

/** The order as the micro-frontend shows it in the task "Check order". */
data class OrderSummary(
    val id: String,
    val status: OrderStatus,
    val customer: Customer,
    val items: List<SummaryItem>,
    val total: BigDecimal,
    val currency: String,
    val checkComment: String?,
) {
    data class SummaryItem(val name: String, val quantity: Int, val price: BigDecimal)

    companion object {
        fun of(order: Order) = OrderSummary(
            id = order.id,
            status = order.status,
            customer = order.customer,
            items = order.lines.map { SummaryItem(it.name, it.quantity, it.price) },
            total = order.total,
            currency = CURRENCY,
            checkComment = order.checkComment,
        )
    }
}

/** The task "Check order": sales approves the order, or declines it with a reason. */
@Component
class CheckOrderForm(
    private val orders: OrderRepository,
    private val userTasks: UserTaskCompletionApi,
) : TaskFormHandler<CheckOrderDecision> {

    override val formRef = "check-order@1"
    override val kind = FormKind.HTML
    override val submissionType = CheckOrderDecision::class.java

    override fun view(context: TaskContext): HtmlFormView {
        val order = orders.findFor(context)
        return HtmlFormView(
            formRef = formRef,
            data = mapOf("order" to OrderSummary.of(order)),
            readOnly = !context.editable || order.status != OrderStatus.CHECKING,
        )
    }

    @Transactional
    override fun complete(context: TaskContext, submission: CheckOrderDecision): CompletionOutcome {
        val approved = submission.approved ?: throw ValidationFailed("approved", "Approve or decline the order.")
        val comment = submission.comment?.trim()?.ifEmpty { null }
        if (!approved && comment == null) throw ValidationFailed("comment", "Give a reason when you decline the order.")

        val order = orders.lockFor(context)
        // A customer cancellation interrupts this task; Camunda would answer 404 for the completion anyway.
        if (order.status != OrderStatus.CHECKING) throw TaskNotActive("Order ${order.id} is no longer being checked.")
        orders.recordCheck(order.id, if (approved) OrderStatus.AWAITING_PAYMENT else OrderStatus.DECLINED, comment)
        return userTasks.completeUserTask(context.userTaskKey, mapOf("approved" to approved))
    }
}

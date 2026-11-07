package io.miragon.tasklist.order.delivery

import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.FieldError
import io.miragon.tasklist.contract.FormKind
import io.miragon.tasklist.contract.JsonFormsView
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.TaskNotActive
import io.miragon.tasklist.contract.ValidationFailed
import io.miragon.tasklist.contract.provider.TaskFormHandler
import io.miragon.tasklist.contract.provider.completeUserTask
import io.miragon.tasklist.order.orders.Order
import io.miragon.tasklist.order.orders.OrderRepository
import io.miragon.tasklist.order.orders.OrderStatus
import io.miragon.tasklist.order.orders.editable
import io.miragon.tasklist.order.orders.findFor
import io.miragon.tasklist.order.orders.lockFor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** What the warehouse may change. Item names, quantities and the address are shown read-only and ignored here. */
data class DeliveryPreparation(
    val items: List<PackedItem> = emptyList(),
    val shippingDate: String? = null,
    val deliveryNote: String? = null,
) {
    data class PackedItem(val itemId: String? = null, val ready: Boolean = false)
}

/** The task "Prepare delivery": the warehouse packs the items and plans the shipping date. It keeps drafts. */
@Component
class PrepareDeliveryForm(
    private val orders: OrderRepository,
    private val userTasks: UserTaskCompletionApi,
) : TaskFormHandler<DeliveryPreparation> {

    override val formRef = "prepare-delivery@1"
    override val kind = FormKind.JSON_FORMS
    override val submissionType = DeliveryPreparation::class.java

    override fun view(context: TaskContext): JsonFormsView {
        val order = orders.findFor(context)
        return JsonFormsView(
            formRef = formRef,
            schema = SCHEMA,
            uiSchema = UI_SCHEMA,
            data = mapOf(
                "orderId" to order.id,
                "shipTo" to "${order.customer.name}\n${order.customer.address}",
                // itemId is not part of the schema: the form keeps it in the data, so the ready flags come back with it.
                "items" to order.lines.map {
                    mapOf("itemId" to it.itemId, "name" to it.name, "quantity" to it.quantity, "ready" to it.ready)
                },
                "shippingDate" to order.shippingDate?.toString(),
                "deliveryNote" to order.deliveryNote,
            ),
            readOnly = !context.editable || order.status != OrderStatus.PREPARING,
            draftable = true,
        )
    }

    @Transactional
    override fun draft(context: TaskContext, submission: DeliveryPreparation) {
        val order = preparing(context)
        val shippingDate = shippingDate(submission)
        orders.recordDelivery(order.id, readyItems(order, submission), shippingDate, note(submission))
    }

    @Transactional
    override fun complete(context: TaskContext, submission: DeliveryPreparation): CompletionOutcome {
        val order = preparing(context)
        val shippingDate = shippingDate(submission)
        val ready = readyItems(order, submission)
        val errors = buildList {
            val unpacked = order.lines.filter { it.itemId !in ready }.joinToString { it.name }
            if (unpacked.isNotEmpty()) add(FieldError("items", "Pack $unpacked before you complete the task."))
            when {
                shippingDate == null -> add(FieldError("shippingDate", "Enter the shipping date."))
                shippingDate.isBefore(LocalDate.now()) -> add(FieldError("shippingDate", "Choose today or a later day."))
            }
        }
        if (errors.isNotEmpty()) throw ValidationFailed("The delivery is not ready yet.", errors)

        orders.recordDelivery(order.id, ready, shippingDate, note(submission))
        return userTasks.completeUserTask(context.userTaskKey)
    }

    private fun preparing(context: TaskContext): Order =
        orders.lockFor(context).also {
            if (it.status != OrderStatus.PREPARING) throw TaskNotActive("Order ${it.id} is no longer being prepared for delivery.")
        }

    private fun readyItems(order: Order, submission: DeliveryPreparation): Set<String> =
        submission.items.filter { it.ready }.mapNotNull { it.itemId }.toSet() intersect order.lines.map { it.itemId }.toSet()

    private fun shippingDate(submission: DeliveryPreparation): LocalDate? =
        submission.shippingDate?.takeIf { it.isNotBlank() }?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                throw ValidationFailed("shippingDate", "Enter the shipping date as YYYY-MM-DD.")
            }
        }

    private fun note(submission: DeliveryPreparation) = submission.deliveryNote?.trim()?.take(500)?.ifEmpty { null }

    private companion object {
        val SCHEMA = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "orderId" to mapOf("type" to "string", "title" to "Order", "readOnly" to true),
                "shipTo" to mapOf("type" to "string", "title" to "Ship to", "readOnly" to true),
                "items" to mapOf(
                    "type" to "array",
                    "title" to "Items",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "name" to mapOf("type" to "string", "title" to "Item", "readOnly" to true),
                            "quantity" to mapOf("type" to "integer", "title" to "Quantity", "readOnly" to true),
                            "ready" to mapOf("type" to "boolean", "title" to "Packed"),
                        ),
                    ),
                ),
                "shippingDate" to mapOf("type" to "string", "format" to "date", "title" to "Shipping date"),
                "deliveryNote" to mapOf("type" to "string", "title" to "Delivery note", "maxLength" to 500),
            ),
            "required" to listOf("shippingDate"),
        )

        val UI_SCHEMA = mapOf(
            "type" to "VerticalLayout",
            "elements" to listOf(
                mapOf(
                    "type" to "HorizontalLayout",
                    "elements" to listOf(control("orderId"), control("shipTo", "multi" to true)),
                ),
                control("items", "disableAdd" to true, "disableRemove" to true),
                control("shippingDate"),
                control("deliveryNote", "multi" to true),
            ),
        )

        private fun control(property: String, vararg options: Pair<String, Any>) =
            mapOf("type" to "Control", "scope" to "#/properties/$property") +
                (if (options.isEmpty()) emptyMap() else mapOf("options" to options.toMap()))
    }
}

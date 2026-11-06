package io.miragon.tasklist.order.placing

import io.miragon.tasklist.contract.FormKind
import io.miragon.tasklist.contract.HtmlFormView
import io.miragon.tasklist.contract.StartFormDescriptor
import io.miragon.tasklist.contract.StartResult
import io.miragon.tasklist.contract.UserContext
import io.miragon.tasklist.contract.provider.StartFormHandler
import io.miragon.tasklist.order.catalog.Catalog
import io.miragon.tasklist.order.orders.CURRENCY
import org.springframework.stereotype.Component

/** The tasklist's "New order" entry: the micro-frontend's stepper, filled with the catalog. */
@Component
class NewOrderStartForm(
    private val catalog: Catalog,
    private val placement: OrderPlacement,
) : StartFormHandler<NewOrder> {

    override val descriptor = StartFormDescriptor(
        id = "new-order",
        formRef = "start-order@1",
        label = "New order",
        description = "Place an order for a MiraVelo customer",
    )
    override val kind = FormKind.HTML
    override val submissionType = NewOrder::class.java

    override fun view(user: UserContext) =
        HtmlFormView(descriptor.formRef, data = mapOf("items" to catalog.items(), "currency" to CURRENCY))

    override fun submit(user: UserContext, submission: NewOrder): StartResult {
        val placed = placement.place(submission)
        return StartResult(placed.processInstanceKey.toString(), placed.orderId)
    }
}

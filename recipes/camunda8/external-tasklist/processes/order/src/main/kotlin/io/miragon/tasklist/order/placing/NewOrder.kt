package io.miragon.tasklist.order.placing

import io.miragon.tasklist.contract.FieldError
import io.miragon.tasklist.order.catalog.CatalogItem
import io.miragon.tasklist.order.orders.Customer

/** What the start form `start-order@1` and `POST /api/v1/orders` submit. */
data class NewOrder(
    val customer: Customer,
    val items: List<NewOrderItem>,
)

data class NewOrderItem(
    val itemId: String,
    val quantity: Int,
)

private val EMAIL = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")
private val QUANTITY = 1..10

/** Checks a new order against the [catalog] and names every field that breaks a rule. */
fun NewOrder.errors(catalog: Map<String, CatalogItem>): List<FieldError> = buildList {
    val (name, email, address) = customer
    if (name.isBlank() || name.length > 100) {
        add(FieldError("customer.name", "Enter the customer's name, up to 100 characters."))
    }
    if (!EMAIL.matches(email.trim()) || email.length > 254) {
        add(FieldError("customer.email", "Enter a valid email address."))
    }
    if (address.isBlank() || address.length > 500) {
        add(FieldError("customer.address", "Enter the delivery address, up to 500 characters."))
    }

    if (items.isEmpty()) add(FieldError("items", "Choose at least one item."))
    items.groupingBy { it.itemId }.eachCount().filterValues { it > 1 }.keys.forEach { itemId ->
        add(FieldError("items", "List ${catalog[itemId]?.name ?: itemId} only once."))
    }
    items.forEach { (itemId, quantity) ->
        val item = catalog[itemId]
        when {
            item == null -> add(FieldError("items", "The shop does not sell '$itemId'."))
            quantity !in QUANTITY -> add(FieldError("items", "Order between 1 and 10 of ${item.name}."))
        }
    }
}

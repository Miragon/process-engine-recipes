package io.miragon.tasklist.order.placing

import io.miragon.tasklist.contract.FieldError
import io.miragon.tasklist.order.catalog.CatalogItem
import io.miragon.tasklist.order.orders.Customer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NewOrderTest {

    private val catalog = listOf(
        CatalogItem("city-helmet", "City helmet", "Helmet", BigDecimal("79.90")),
        CatalogItem("folding-lock", "Folding lock", "Lock", BigDecimal("50.00")),
    ).associateBy { it.id }

    private val alice = Customer("Alice Smith", "alice@example.com", "Hauptstraße 1\n80331 München")

    @Test
    fun `accepts a complete order`() {
        val order = NewOrder(alice, listOf(NewOrderItem("city-helmet", 1), NewOrderItem("folding-lock", 10)))

        assertThat(order.errors(catalog)).isEmpty()
    }

    @Test
    fun `names every customer field that is missing or invalid`() {
        val order = NewOrder(Customer(" ", "alice@example", ""), listOf(NewOrderItem("city-helmet", 1)))

        assertThat(order.errors(catalog).map { it.field })
            .containsExactly("customer.name", "customer.email", "customer.address")
    }

    @Test
    fun `needs at least one item`() {
        assertThat(NewOrder(alice, emptyList()).errors(catalog))
            .containsExactly(FieldError("items", "Choose at least one item."))
    }

    @Test
    fun `allows 1 to 10 pieces of known items, each listed once`() {
        val order = NewOrder(
            alice,
            listOf(
                NewOrderItem("city-helmet", 0),
                NewOrderItem("folding-lock", 11),
                NewOrderItem("folding-lock", 1),
                NewOrderItem("unicycle", 1),
            ),
        )

        assertThat(order.errors(catalog)).containsExactly(
            FieldError("items", "List Folding lock only once."),
            FieldError("items", "Order between 1 and 10 of City helmet."),
            FieldError("items", "Order between 1 and 10 of Folding lock."),
            FieldError("items", "The shop does not sell 'unicycle'."),
        )
    }
}

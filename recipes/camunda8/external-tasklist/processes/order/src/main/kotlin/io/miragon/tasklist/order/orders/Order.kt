package io.miragon.tasklist.order.orders

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

enum class OrderStatus { CHECKING, CANCELED, DECLINED, AWAITING_PAYMENT, PREPARING, SHIPPED, NOT_PAID }

enum class PaymentStatus { PAID, REJECTED }

data class Customer(
    val name: String,
    val email: String,
    val address: String,
)

data class OrderLine(
    val itemId: String,
    val name: String,
    val quantity: Int,
    val price: BigDecimal,
    val ready: Boolean,
)

data class Order(
    val id: String,
    val status: OrderStatus,
    val customer: Customer,
    val lines: List<OrderLine>,
    val total: BigDecimal,
    val checkComment: String?,
    val cancelReason: String?,
    val paymentId: String?,
    val paymentStatus: PaymentStatus?,
    val paymentReason: String?,
    val shippingDate: LocalDate?,
    val deliveryNote: String?,
    val processInstanceKey: Long?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

class OrderNotFound(orderId: String) : RuntimeException("There is no order $orderId.")

const val CURRENCY = "EUR"

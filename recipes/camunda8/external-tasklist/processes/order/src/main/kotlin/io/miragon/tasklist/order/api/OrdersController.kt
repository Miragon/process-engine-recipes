package io.miragon.tasklist.order.api

import io.miragon.tasklist.contract.FieldError
import io.miragon.tasklist.order.cancellation.OrderCancellation
import io.miragon.tasklist.order.orders.CURRENCY
import io.miragon.tasklist.order.orders.Customer
import io.miragon.tasklist.order.orders.Order
import io.miragon.tasklist.order.orders.OrderNotFound
import io.miragon.tasklist.order.orders.OrderRepository
import io.miragon.tasklist.order.orders.OrderStatus
import io.miragon.tasklist.order.orders.PaymentStatus
import io.miragon.tasklist.order.payment.PaymentResult
import io.miragon.tasklist.order.payment.PaymentResults
import io.miragon.tasklist.order.placing.NewOrder
import io.miragon.tasklist.order.placing.OrderPlacement
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

private const val PROBLEM = "application/problem+json"

@OpenAPIDefinition(
    info = Info(
        title = "Order application",
        version = "v1",
        description = "Orders of the MiraVelo bike shop. The payment application reports payment results here.",
    ),
)
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
class OrdersController(
    private val placement: OrderPlacement,
    private val orders: OrderRepository,
    private val paymentResults: PaymentResults,
    private val cancellation: OrderCancellation,
) {

    @Operation(summary = "Place an order", description = "Does the same as the tasklist's start form \"New order\".")
    @ApiResponse(responseCode = "201", description = "The order is placed and its process started.")
    @ApiResponse(
        responseCode = "422",
        description = "The order breaks a rule; `errors` names the fields.",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = Problem::class))],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun place(@RequestBody order: NewOrder): OrderPlaced =
        placement.place(order).let { OrderPlaced(it.orderId, it.processInstanceKey.toString()) }

    @Operation(summary = "Read an order")
    @ApiResponse(responseCode = "200", description = "The order.")
    @ApiResponse(
        responseCode = "404",
        description = "There is no such order.",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = Problem::class))],
    )
    @GetMapping("/{orderId}")
    fun get(@PathVariable orderId: String): OrderDetails =
        OrderDetails.of(orders.find(orderId) ?: throw OrderNotFound(orderId))

    @Operation(
        summary = "Report the result of a payment",
        description = "Called by the payment application. Reporting the same result again is fine.",
    )
    @ApiResponse(responseCode = "204", description = "The result is applied, now or before.")
    @ApiResponse(
        responseCode = "404",
        description = "There is no such order.",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = Problem::class))],
    )
    @ApiResponse(
        responseCode = "409",
        description = "The result contradicts an earlier one.",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = Problem::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "The order process does not wait for a result yet. Report it again after `Retry-After` seconds.",
        headers = [Header(name = "Retry-After", schema = Schema(type = "integer"))],
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = Problem::class))],
    )
    @PostMapping("/{orderId}/payment-result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reportPaymentResult(@PathVariable orderId: String, @RequestBody result: PaymentResult) =
        paymentResults.apply(orderId, result)

    @Operation(
        summary = "Cancel an order",
        description = "A customer cancels the order. That works while sales checks it; canceling again is fine.",
    )
    @ApiResponse(responseCode = "204", description = "The order is canceled, now or before.")
    @ApiResponse(
        responseCode = "404",
        description = "There is no such order.",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = Problem::class))],
    )
    @ApiResponse(
        responseCode = "409",
        description = "The order can no longer be canceled.",
        content = [Content(mediaType = PROBLEM, schema = Schema(implementation = Problem::class))],
    )
    @PostMapping("/{orderId}/cancellation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@PathVariable orderId: String, @RequestBody(required = false) request: Cancellation?) =
        cancellation.cancel(orderId, request?.reason)
}

data class OrderPlaced(val orderId: String, val processInstanceKey: String)

/** How the API reports an error, as `application/problem+json`. Only describes the answer in the API document. */
data class Problem(
    val type: String?,
    val title: String,
    val status: Int,
    val detail: String?,
    val instance: String?,
    @field:Schema(description = "ORDER_NOT_FOUND, CONFLICT, VALIDATION_FAILED, PROCESS_NOT_WAITING or BAD_REQUEST")
    val code: String,
    @field:Schema(description = "The fields that break a rule, only for VALIDATION_FAILED")
    val errors: List<FieldError>?,
)

data class Cancellation(val reason: String? = null)

data class OrderDetails(
    val orderId: String,
    val status: OrderStatus,
    val customer: Customer,
    val items: List<OrderItem>,
    val total: BigDecimal,
    val currency: String,
    val checkComment: String?,
    val cancelReason: String?,
    val payment: OrderPayment?,
    val shippingDate: LocalDate?,
    val deliveryNote: String?,
    val processInstanceKey: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    data class OrderItem(val itemId: String, val name: String, val quantity: Int, val price: BigDecimal, val ready: Boolean)

    data class OrderPayment(val paymentId: String?, val status: PaymentStatus?, val reason: String?)

    companion object {
        fun of(order: Order) = OrderDetails(
            orderId = order.id,
            status = order.status,
            customer = order.customer,
            items = order.lines.map { OrderItem(it.itemId, it.name, it.quantity, it.price, it.ready) },
            total = order.total,
            currency = CURRENCY,
            checkComment = order.checkComment,
            cancelReason = order.cancelReason,
            payment = order.paymentId?.let { OrderPayment(it, order.paymentStatus, order.paymentReason) },
            shippingDate = order.shippingDate,
            deliveryNote = order.deliveryNote,
            processInstanceKey = order.processInstanceKey?.toString(),
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
        )
    }
}

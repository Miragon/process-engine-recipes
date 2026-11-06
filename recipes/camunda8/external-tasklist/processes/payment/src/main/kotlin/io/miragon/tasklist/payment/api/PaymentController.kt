package io.miragon.tasklist.payment.api

import io.miragon.tasklist.payment.Customer
import io.miragon.tasklist.payment.Payment
import io.miragon.tasklist.payment.PaymentRepository
import io.miragon.tasklist.payment.PaymentStatus
import io.miragon.tasklist.payment.report.CallbackProperties
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.net.URI
import java.time.Instant

/** The payments API for other services (docs/architecture.md §5). */
@OpenAPIDefinition(
    info = Info(
        title = "Payment application",
        version = "v1",
        description = "Payments of orders. A payment starts the payment process; its result goes to the callback URL.",
    ),
)
@Tag(name = "Payments")
@RestController
@RequestMapping(PaymentController.PATH)
class PaymentController(
    private val requests: PaymentRequests,
    private val payments: PaymentRepository,
    private val callbacks: CallbackProperties,
) {
    @PostMapping
    @Operation(
        summary = "Request the payment of an order",
        description = "Stores the payment and starts its process. Repeating a request for the same order and amount is safe.",
        responses = [
            ApiResponse(responseCode = "201", description = "Payment created and its process started", useReturnTypeSchema = true),
            ApiResponse(responseCode = "200", description = "The order was requested with this amount before", useReturnTypeSchema = true),
            ApiResponse(
                responseCode = "400",
                description = "The request is invalid, or the host of the callback URL is not allowed",
                content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "The order was requested with another amount before",
                content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "503",
                description = "Camunda did not accept the start of the process; nothing was stored, try again",
                content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    fun request(@RequestBody request: PaymentRequest): ResponseEntity<PaymentReference> {
        val violations = request.violations(callbacks)
        if (violations.isNotEmpty()) throw problem(HttpStatus.BAD_REQUEST, "The payment request is invalid.", "errors" to violations)
        return when (val outcome = requests.request(request)) {
            is RequestOutcome.Created ->
                ResponseEntity.created(URI.create("$PATH/${outcome.paymentId}")).body(PaymentReference(outcome.paymentId))
            is RequestOutcome.Repeated -> ResponseEntity.ok(PaymentReference(outcome.paymentId))
            is RequestOutcome.Conflicting -> throw problem(
                HttpStatus.CONFLICT,
                "Order ${request.orderId} was requested with ${outcome.existing.invoiceAmount} before, as payment ${outcome.existing.id}.",
                "paymentId" to outcome.existing.id,
            )
        }
    }

    @GetMapping("/{paymentId}")
    @Operation(
        summary = "Read a payment",
        responses = [
            ApiResponse(responseCode = "200", description = "The payment", useReturnTypeSchema = true),
            ApiResponse(
                responseCode = "404",
                description = "There is no payment with this ID",
                content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    fun payment(@PathVariable paymentId: String): PaymentDetails =
        payments.find(paymentId)?.let(PaymentDetails::of)
            ?: throw problem(HttpStatus.NOT_FOUND, "There is no payment $paymentId.")

    companion object {
        const val PATH = "/api/v1/payments"
    }
}

data class PaymentReference(val paymentId: String)

data class PaymentDetails(
    val paymentId: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val customer: Customer,
    val callbackUrl: String,
    val status: PaymentStatus,
    val amountReceived: BigDecimal?,
    val rejectionReason: String?,
    val remindersSent: Int,
    @field:Schema(description = "A string, because Camunda keys exceed the integer range of JavaScript")
    val processInstanceKey: String?,
    val createdAt: Instant,
    val decidedAt: Instant?,
    val reportedAt: Instant?,
) {
    companion object {
        fun of(payment: Payment) = with(payment) {
            PaymentDetails(
                id, orderId, amount, currency, customer, callbackUrl, status, amountReceived, rejectionReason,
                remindersSent, processInstanceKey?.toString(), createdAt, decidedAt, reportedAt,
            )
        }
    }
}

private fun problem(status: HttpStatus, detail: String, vararg properties: Pair<String, Any>) =
    ErrorResponseException(
        status,
        ProblemDetail.forStatusAndDetail(status, detail).apply { properties.forEach { (name, value) -> setProperty(name, value) } },
        null,
    )

package io.miragon.tasklist.fakepayment

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stands in for `POST /api/v1/payments` of the payment application. It lives outside the application's package,
 * so only the tests that import it serve it.
 */
@RestController
class FakePaymentApp {

    data class Received(val body: Map<String, Any?>, val at: Instant)

    val requests = CopyOnWriteArrayList<Received>()
    private val unavailable = AtomicInteger()

    @Volatile
    private var refusal: HttpStatus? = null

    /** Answers the next [times] requests with 503. */
    fun failNext(times: Int) = unavailable.set(times)

    /** Answers every request with [status] and a problem detail. */
    fun refuseWith(status: HttpStatus) {
        refusal = status
    }

    fun reset() {
        requests.clear()
        unavailable.set(0)
        refusal = null
    }

    fun requestsFor(orderId: String) = requests.filter { it.body["orderId"] == orderId }

    @PostMapping("/api/v1/payments")
    fun requestPayment(@RequestBody body: Map<String, Any?>): ResponseEntity<Any> {
        requests += Received(body, Instant.now())
        if (unavailable.getAndDecrement() > 0) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        refusal?.let { return ResponseEntity.status(it).body(ProblemDetail.forStatusAndDetail(it, "The amount differs from the first request.")) }
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("paymentId" to "PAY-${body["orderId"]}"))
    }
}

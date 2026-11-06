package io.miragon.tasklist.payment

import org.springframework.boot.test.context.TestComponent
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/** Stands in for the order application: keeps the payment results it receives and answers as the test says. */
@TestComponent
@RestController
@RequestMapping("/fake-order-app/api/v1/orders")
class FakeOrderApplication {

    val results: MutableList<Map<String, Any?>> = CopyOnWriteArrayList()
    private val answers = ConcurrentLinkedQueue<HttpStatus>()

    /** The next results get these answers, all later ones `204`. */
    fun answerNext(vararg statuses: HttpStatus) {
        answers += statuses
    }

    fun reset() {
        results.clear()
        answers.clear()
    }

    @PostMapping("/{orderId}/payment-result")
    fun paymentResult(@PathVariable orderId: String, @RequestBody result: Map<String, Any?>): ResponseEntity<Any> {
        results += mapOf("orderId" to orderId) + result
        val status = answers.poll() ?: return ResponseEntity.noContent().build()
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, "The fake order application answers $status."))
    }
}

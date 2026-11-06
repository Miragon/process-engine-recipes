package io.miragon.tasklist.order.payment

import io.miragon.tasklist.order.OrderProperties
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Duration

/** `POST /api/v1/payments` of the payment application. */
data class PaymentRequest(
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val customer: Payer,
    val callbackUrl: String,
) {
    data class Payer(val name: String, val email: String)
}

data class PaymentCreated(val paymentId: String)

sealed interface PaymentRequestOutcome {
    data class Accepted(val paymentId: String) : PaymentRequestOutcome

    /** The payment application refused the request; asking again would not change its mind. */
    data class Refused(val reason: String) : PaymentRequestOutcome

    /** The payment application did not answer properly; it may well do so later. */
    data class Unavailable(val reason: String) : PaymentRequestOutcome
}

@Component
class PaymentsClient(builder: RestClient.Builder, properties: OrderProperties) {

    /**
     * The JDK's HTTP client, because it never repeats a request on its own (Apache's client would repeat a 503).
     * Retries belong to Camunda: it retries the job with a backoff and shows the attempts in Operate.
     */
    private val http = builder
        .baseUrl(properties.payments.baseUrl)
        .requestFactory(
            ClientHttpRequestFactoryBuilder.jdk()
                .build(HttpClientSettings.defaults().withTimeouts(Duration.ofSeconds(2), Duration.ofSeconds(10))),
        )
        .build()

    /** Asks the payment application to collect a payment; it answers 201 for a new request and 200 for a repeated one. */
    fun requestPayment(request: PaymentRequest): PaymentRequestOutcome =
        try {
            http.post().uri("/api/v1/payments").body(request).exchange { _, response ->
                val status = response.statusCode
                when {
                    status.is2xxSuccessful -> PaymentRequestOutcome.Accepted(response.bodyTo(PaymentCreated::class.java)!!.paymentId)
                    status.is4xxClientError && status.value() !in RETRYABLE ->
                        PaymentRequestOutcome.Refused(
                            "The payment application refused the payment request with ${status.value()}: " +
                                (runCatching { response.bodyTo(ProblemDetail::class.java)?.detail }.getOrNull() ?: "no reason given"),
                        )
                    else -> PaymentRequestOutcome.Unavailable("The payment application answered ${status.value()}.")
                }
            }!!
        } catch (e: ResourceAccessException) {
            PaymentRequestOutcome.Unavailable("The payment application did not answer: ${e.message}")
        }

    private companion object {
        val RETRYABLE = setOf(HttpStatus.REQUEST_TIMEOUT.value(), HttpStatus.TOO_MANY_REQUESTS.value())
    }
}

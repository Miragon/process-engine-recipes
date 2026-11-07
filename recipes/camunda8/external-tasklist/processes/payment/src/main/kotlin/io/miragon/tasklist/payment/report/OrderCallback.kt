package io.miragon.tasklist.payment.report

import io.miragon.tasklist.payment.Payment
import io.miragon.tasklist.payment.PaymentStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.http.HttpClient

/** The body the order application expects at its callback URL. */
data class PaymentResult(val paymentId: String, val status: PaymentStatus, val reason: String?)

/** What became of a payment result sent to the order application. */
sealed interface Delivery {
    data object Accepted : Delivery

    /** The order application refused the result. Retrying cannot help; a person has to look at it. */
    data class Refused(val reason: String) : Delivery

    /** The order application could not take the result now. A later retry may succeed. */
    data class Failed(val reason: String) : Delivery
}

/** Posts payment results to the callback URL that came with the payment request. */
@Component
class OrderCallback(properties: CallbackProperties) {

    /**
     * The JDK client, set explicitly: never follows a redirect, which could lead to a host that is not
     * on the allow-list, and never retries. The Apache client that comes with the Camunda client would
     * repeat a 503 on its own, behind the back of the job's retries.
     */
    private val http = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(properties.connectTimeout)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build(),
            ).apply { setReadTimeout(properties.readTimeout) },
        )
        .build()

    fun send(payment: Payment): Delivery =
        try {
            http.post()
                .uri(URI(payment.callbackUrl))
                .contentType(MediaType.APPLICATION_JSON)
                .body(PaymentResult(payment.id, payment.status, payment.rejectionReason))
                .exchangeForRequiredValue { _, response -> delivery(response.statusCode, response.bodyTo(String::class.java)) }
        } catch (e: ResourceAccessException) {
            Delivery.Failed("The order application did not answer: ${e.message}")
        }

    private fun delivery(status: HttpStatusCode, body: String?): Delivery {
        val answer = "The order application answered $status" + body?.takeIf { it.isNotBlank() }?.let { ": ${it.take(500)}" }.orEmpty()
        return when {
            status.is2xxSuccessful -> Delivery.Accepted
            status.is5xxServerError -> Delivery.Failed(answer)
            else -> Delivery.Refused(answer)
        }
    }
}

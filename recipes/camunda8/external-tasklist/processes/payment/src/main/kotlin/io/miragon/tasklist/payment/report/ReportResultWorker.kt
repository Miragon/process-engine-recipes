package io.miragon.tasklist.payment.report

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobClient
import io.miragon.tasklist.payment.PaymentRepository
import io.miragon.tasklist.payment.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Reports the decided payment to the order application and answers the job as docs/architecture.md §5 asks:
 * complete on 2xx, an incident for a person on a refusal, a retry with exponential backoff on 5xx or no answer.
 * The worker sends the job commands itself, so an incident shows the order application's answer, not a stack trace.
 */
@Component
class ReportResultWorker(
    private val payments: PaymentRepository,
    private val orderCallback: OrderCallback,
    private val properties: CallbackProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @JobWorker(type = "payment.report-result", autoComplete = [false])
    fun report(client: JobClient, job: ActivatedJob, @Variable paymentId: String) {
        val payment = checkNotNull(payments.find(paymentId)) { "There is no payment $paymentId." }
        check(payment.status != PaymentStatus.OPEN) { "Payment $paymentId has no result yet." }
        when (val delivery = orderCallback.send(payment)) {
            Delivery.Accepted -> {
                payments.markReported(payment.id)
                client.newCompleteCommand(job).send().join()
            }
            is Delivery.Refused -> {
                log.warn("Reporting payment {} needs a person, raising an incident. {}", paymentId, delivery.reason)
                client.newFailCommand(job).retries(0).errorMessage(delivery.reason).send().join()
            }
            is Delivery.Failed -> {
                val backoff = backoff(job.retries)
                log.warn("Reporting payment {} failed, {} retries left, next in {}. {}", paymentId, job.retries - 1, backoff, delivery.reason)
                client.newFailCommand(job)
                    .retries(job.retries - 1)
                    .retryBackoff(backoff)
                    .errorMessage(delivery.reason)
                    .send().join()
            }
        }
    }

    /** 5 s, 10 s, 20 s and 40 s by default: the wait doubles with every failed attempt. */
    private fun backoff(retriesLeft: Int): Duration =
        properties.retryBackoff.multipliedBy(1L shl (RETRIES - retriesLeft).coerceIn(0, 10))

    private companion object {
        /** The retries of both "Report payment result" tasks in payment.bpmn. */
        const val RETRIES = 5
    }
}

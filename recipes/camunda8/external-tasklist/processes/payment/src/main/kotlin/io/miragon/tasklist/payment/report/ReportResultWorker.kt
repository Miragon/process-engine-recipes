package io.miragon.tasklist.payment.report

import io.miragon.tasklist.payment.PaymentRepository
import io.miragon.tasklist.payment.PaymentStatus
import io.miragon.tasklist.payment.engine.IncidentRequired
import io.miragon.tasklist.payment.engine.RetryLater
import io.miragon.tasklist.payment.engine.ServiceTask
import io.miragon.tasklist.payment.engine.ServiceTaskWorkers
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Reports the decided payment to the order application and answers the job as docs/architecture.md §5 asks:
 * complete on 2xx, an incident for a person on a refusal, a retry with exponential backoff on 5xx or no answer.
 * Both failures carry the order application's answer, so an incident shows it instead of a stack trace.
 *
 * The result comes from the process variables, not from the payment row: the completion of "Check payment" commits
 * the row only after Camunda accepted it, so this job may run before that commit is visible.
 */
@Component
class ReportResultWorker(
    private val payments: PaymentRepository,
    private val orderCallback: OrderCallback,
    private val properties: CallbackProperties,
    workers: ServiceTaskWorkers,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        workers.register("payment.report-result", setOf("paymentId", "paymentStatus", "rejectionReason"), ::report)
    }

    private fun report(job: ServiceTask): Map<String, Any?> {
        val paymentId = job.string("paymentId")
        val stored = checkNotNull(payments.find(paymentId)) { "There is no payment $paymentId." }
        val payment = stored.copy(status = PaymentStatus.valueOf(job.string("paymentStatus")), rejectionReason = job.stringOrNull("rejectionReason"))
        check(payment.status != PaymentStatus.OPEN) { "Payment $paymentId has no result yet." }
        when (val delivery = orderCallback.send(payment)) {
            Delivery.Accepted -> payments.markReported(payment.id)
            is Delivery.Refused -> {
                log.warn("Reporting payment {} needs a person, raising an incident. {}", paymentId, delivery.reason)
                throw IncidentRequired(delivery.reason)
            }
            is Delivery.Failed -> {
                val backoff = backoff(job.retries)
                log.warn("Reporting payment {} failed, {} retries left, next in {}. {}", paymentId, job.retries - 1, backoff, delivery.reason)
                throw RetryLater(delivery.reason, backoff)
            }
        }
        return emptyMap()
    }

    /** 5 s, 10 s, 20 s and 40 s by default: the wait doubles with every failed attempt. */
    private fun backoff(retriesLeft: Int): Duration =
        properties.retryBackoff.multipliedBy(1L shl (RETRIES - retriesLeft).coerceIn(0, 10))

    private companion object {
        /** The retries of both "Report payment result" tasks in payment.bpmn. */
        const val RETRIES = 5
    }
}

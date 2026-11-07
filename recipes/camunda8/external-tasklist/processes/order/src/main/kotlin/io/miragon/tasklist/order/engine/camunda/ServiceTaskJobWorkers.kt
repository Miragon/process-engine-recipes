package io.miragon.tasklist.order.engine.camunda

import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.toTaskInformation
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle
import dev.bpmcrafters.processengineapi.impl.task.filterBySubscription
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskType
import io.camunda.client.CamundaClient
import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobWorker
import io.camunda.client.lifecycle.CamundaClientLifecycleAware
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Opens a job worker per service task subscription when a Camunda client starts and closes them when it stops, as
 * Camunda's `@JobWorker` does: once in production, once per test under Camunda Process Test. The C8 adapter's own
 * delivery opens its workers once and keeps no handle to close them, so they would keep fetching jobs during shutdown.
 */
@Component
@ConditionalOnProperty("camunda.client.worker.defaults.enabled", havingValue = "true", matchIfMissing = true)
class ServiceTaskJobWorkers(
    private val subscriptions: SubscriptionRepository,
    @Value("\${spring.application.name}") private val workerName: String,
) : CamundaClientLifecycleAware {

    private val workers = mutableListOf<JobWorker>()

    @Synchronized
    override fun onStart(client: CamundaClient) {
        onStop(client)
        for (subscription in subscriptions.getTaskSubscriptions().filter { it.taskType == TaskType.EXTERNAL }) {
            workers += client.newWorker()
                .jobType(subscription.taskDescriptionKey)
                .handler { _, job -> deliver(subscription, job) }
                .name(workerName)
                .timeout(Duration.ofMinutes(5))
                .apply { subscription.payloadDescription?.takeIf { it.isNotEmpty() }?.let { fetchVariables(it.toList()) } }
                .open()
        }
    }

    @Synchronized
    override fun onStop(client: CamundaClient) {
        workers.forEach(JobWorker::close)
        workers.clear()
    }

    /** The subscription's handler answers every job itself, so no job is tracked for a termination callback. */
    private fun deliver(subscription: TaskSubscriptionHandle, job: ActivatedJob) =
        subscription.action.accept(job.toTaskInformation().withReason(TaskInformation.CREATE), job.variablesAsMap.filterBySubscription(subscription))
}

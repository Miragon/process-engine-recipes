package io.miragon.tasklist.taskmanager.camunda

import io.camunda.client.annotation.JobWorker
import io.camunda.client.api.command.ClientHttpException
import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobClient
import io.miragon.tasklist.taskmanager.TaskmanagerProperties
import io.miragon.tasklist.taskmanager.projection.ProjectionService
import io.miragon.tasklist.taskmanager.projection.TaskDetailsResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Handles the jobs of the global user task listener. Camunda waits for each job before it moves the task on,
 * so the projection sees every transition in order, including cancellations by boundary events.
 */
@Component
class ProjectionWorker(
    private val projection: ProjectionService,
    private val details: TaskDetailsResolver,
    private val properties: TaskmanagerProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @JobWorker(type = TaskmanagerProperties.Listener.TYPE, autoComplete = [false], fetchVariables = ["subject"])
    fun onUserTaskEvent(client: JobClient, job: ActivatedJob) {
        try {
            job.toUserTaskEvent()?.let { event -> projection.apply(event, details.cachedOrLoadLater(event)) }
        } catch (e: RuntimeException) {
            fail(client, job, e)
            return
        }
        try {
            // A task listener job must be completed without variables.
            client.newCompleteCommand(job.key).send().join()
        } catch (e: ClientHttpException) {
            if (e.code() != 404) throw e
            // The process instance was canceled meanwhile; its canceling listener or the reconciler closes the task.
            log.info("Listener job {} disappeared before it was completed", job.key)
        }
    }

    /**
     * The database may be down for a moment (a restart, a failover). Retry with a growing pause, so the task
     * waits instead of running out of retries at once; when they are spent, Camunda raises an incident.
     */
    private fun fail(client: JobClient, job: ActivatedJob, e: RuntimeException) {
        val attempt = (properties.listener.retries - job.retries).coerceAtLeast(0)
        val backoff = minOf(Duration.ofSeconds(2).multipliedBy(1L shl attempt.coerceAtMost(10)), Duration.ofMinutes(1))
        log.warn("Projecting listener job {} failed, {} retries left, next in {}: {}", job.key, job.retries - 1, backoff, e.message)
        client.newFailCommand(job.key)
            .retries(job.retries - 1)
            .retryBackoff(backoff)
            .errorMessage("The task manager could not store the user task event: ${e.message}")
            .send()
            .join()
    }
}

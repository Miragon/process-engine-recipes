package io.miragon.tasklist.taskmanager.projection

import io.camunda.client.annotation.JobWorker
import io.camunda.client.api.command.ClientHttpException
import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobClient
import io.miragon.tasklist.taskmanager.TaskmanagerProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Handles the jobs of the global user task listener. Camunda waits for each job before it moves the task on,
 * so the projection sees every transition in order, including cancellations by boundary events.
 */
@Component
class ProjectionWorker(
    private val projection: ProjectionService,
    private val definitions: ProcessDefinitions,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @JobWorker(type = TaskmanagerProperties.Listener.TYPE, autoComplete = [false], fetchVariables = ["subject"])
    fun onUserTaskEvent(client: JobClient, job: ActivatedJob) {
        UserTaskEvent.from(job)?.let { event ->
            val details = definitions.task(event.processDefinitionKey, event.processDefinitionId, event.elementId)
            projection.apply(event, details)
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
}

package io.miragon.tasklist.taskmanager.reconcile

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientHttpException
import io.camunda.client.api.search.enums.UserTaskState
import io.camunda.client.api.search.response.UserTask
import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.contract.TasklistApi
import io.miragon.tasklist.taskmanager.TaskmanagerProperties
import io.miragon.tasklist.taskmanager.projection.CamundaTask
import io.miragon.tasklist.taskmanager.projection.ProjectionService
import io.miragon.tasklist.taskmanager.projection.TaskDetails
import io.miragon.tasklist.taskmanager.projection.TaskRepository
import io.miragon.tasklist.taskmanager.projection.UserTaskRow
import io.miragon.tasklist.taskmanager.api.TaskReportedInactive
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/**
 * Compares the projection with Camunda's search and corrects what the listener cannot see:
 * tasks ended by a process instance modification (it skips the canceling listener) and tasks
 * created before the listener was registered. Search results lag behind, so it only looks at
 * tasks that have been quiet for a while and never reopens a closed one.
 */
@Component
class Reconciler(
    private val tasks: TaskRepository,
    private val projection: ProjectionService,
    private val camunda: CamundaClient,
    properties: TaskmanagerProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val settings = properties.reconciler

    @Scheduled(fixedDelayString = "\${taskmanager.reconciler.interval}", initialDelayString = "\${taskmanager.reconciler.interval}")
    fun run() {
        try {
            reconcileOnce()
        } catch (e: RuntimeException) {
            log.warn("Reconciliation failed, trying again next round: {}", e.message)
        }
    }

    /** Returns the number of corrected tasks. */
    fun reconcileOnce(now: OffsetDateTime = OffsetDateTime.now()): Int {
        val quietSince = now.minus(settings.lag)
        val corrected = tasks.findOpenToVerify(quietSince, settings.batchSize).count { verify(it, now) }
        return corrected + discoverMissing(quietSince, now)
    }

    @EventListener
    fun onTaskReportedInactive(event: TaskReportedInactive) {
        runCatching { verify(event.task) }
            .onFailure { log.warn("Checking task {} failed: {}", event.task.userTaskKey, it.message) }
    }

    /** Checks one open task against Camunda; returns true if it was closed. */
    fun verify(task: UserTaskRow, now: OffsetDateTime = OffsetDateTime.now()): Boolean {
        val camundaTask = try {
            camunda.newUserTaskGetRequest(task.userTaskKey).send().join()
        } catch (e: ClientHttpException) {
            if (e.code() != 404) throw e
            null
        }
        return when {
            camundaTask == null ->
                task.createdAt.isBefore(now.minus(settings.missingGrace)) &&
                    projection.closeByReconciler(task, TaskState.CANCELED, now)
            camundaTask.state == UserTaskState.COMPLETED -> projection.closeByReconciler(task, TaskState.COMPLETED, now)
            camundaTask.state == UserTaskState.CANCELED || camundaTask.state == UserTaskState.FAILED ->
                projection.closeByReconciler(task, TaskState.CANCELED, now)
            else -> {
                tasks.fillDetails(task.userTaskKey, TaskDetails(camundaTask.name, camundaTask.processName, camundaTask.externalFormReference))
                tasks.markVerified(task.userTaskKey, now)
                false
            }
        }
    }

    private fun discoverMissing(quietSince: OffsetDateTime, now: OffsetDateTime): Int =
        camunda.newUserTaskSearchRequest()
            .filter { it.state(UserTaskState.CREATED) }
            .sort { it.creationDate().asc() }
            .page { it.limit(settings.batchSize) }
            .send()
            .join()
            .items()
            .filter { it.creationDate.isBefore(quietSince) && !tasks.exists(it.userTaskKey) }
            .count { projection.insertMissing(it.toCamundaTask(), now) }

    private fun UserTask.toCamundaTask() = CamundaTask(
        userTaskKey = userTaskKey,
        elementInstanceKey = elementInstanceKey,
        processInstanceKey = processInstanceKey,
        processDefinitionKey = processDefinitionKey,
        processDefinitionId = bpmnProcessId,
        processDefinitionVersion = processDefinitionVersion,
        elementId = elementId,
        tenantId = tenantId,
        name = name,
        processName = processName,
        domain = customHeaders[TasklistApi.DOMAIN_HEADER]?.takeIf { it.isNotBlank() } ?: bpmnProcessId,
        businessId = businessId,
        formRef = externalFormReference,
        assignee = assignee?.takeIf { it.isNotBlank() },
        candidateGroups = candidateGroups.orEmpty(),
        candidateUsers = candidateUsers.orEmpty(),
        priority = priority ?: 50,
        dueDate = dueDate,
        followUpDate = followUpDate,
        createdAt = creationDate,
    )
}

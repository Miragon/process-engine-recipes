package io.miragon.tasklist.taskmanager.camunda

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientHttpException
import io.camunda.client.api.search.enums.UserTaskState
import io.camunda.client.api.search.response.UserTask
import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.contract.TasklistApi
import io.miragon.tasklist.taskmanager.projection.CamundaTask
import io.miragon.tasklist.taskmanager.projection.EngineUserTask
import io.miragon.tasklist.taskmanager.projection.EngineUserTasks
import io.miragon.tasklist.taskmanager.projection.TaskDetails
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/** [EngineUserTasks] from Camunda's eventually consistent search. */
@Component
class CamundaUserTasks(private val camunda: CamundaClient) : EngineUserTasks {

    override fun find(userTaskKey: Long): EngineUserTask? {
        val task = try {
            camunda.newUserTaskGetRequest(userTaskKey).send().join()
        } catch (e: ClientHttpException) {
            if (e.code() == 404) return null
            throw e
        }
        val state = when (task.state) {
            UserTaskState.COMPLETED -> TaskState.COMPLETED
            UserTaskState.CANCELED, UserTaskState.FAILED -> TaskState.CANCELED
            else -> TaskState.OPEN
        }
        return EngineUserTask(state, TaskDetails(task.name, task.processName, task.externalFormReference))
    }

    override fun openCreatedBefore(createdBefore: OffsetDateTime, from: Int, limit: Int): List<CamundaTask> =
        camunda.newUserTaskSearchRequest()
            .filter { it.state(UserTaskState.CREATED).creationDate { date -> date.lt(createdBefore) } }
            .sort { it.creationDate().asc() }
            .page { it.from(from).limit(limit) }
            .send()
            .join()
            .items()
            .map { it.toCamundaTask() }

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

package io.miragon.tasklist.taskmanager.projection

import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.search.enums.JobKind
import io.camunda.client.api.search.enums.ListenerEventType
import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.contract.TasklistApi
import java.time.OffsetDateTime

/** One lifecycle transition of a user task, as the global listener job reports it. */
data class UserTaskEvent(
    val jobKey: Long,
    val type: EventType,
    val userTaskKey: Long,
    val elementInstanceKey: Long,
    val processInstanceKey: Long,
    val processDefinitionKey: Long,
    val processDefinitionId: String,
    val processDefinitionVersion: Int,
    val elementId: String,
    val tenantId: String,
    val domain: String,
    val businessId: String?,
    val subject: String?,
    val assignee: String?,
    val candidateGroups: List<String>,
    val candidateUsers: List<String>,
    val priority: Int,
    val dueDate: OffsetDateTime?,
    val followUpDate: OffsetDateTime?,
    val action: String?,
    val changedAttributes: List<String>,
) {
    enum class EventType(val state: TaskState) {
        CREATING(TaskState.OPEN),
        ASSIGNING(TaskState.OPEN),
        UPDATING(TaskState.OPEN),
        COMPLETING(TaskState.COMPLETED),
        CANCELING(TaskState.CANCELED),
    }

    companion object {
        private const val SUBJECT_MAX_LENGTH = 500

        /** Reads a task listener job; returns null for anything else, which the worker just completes. */
        fun from(job: ActivatedJob): UserTaskEvent? {
            if (job.kind != JobKind.TASK_LISTENER) return null
            val type = when (job.listenerEventType) {
                ListenerEventType.CREATING -> EventType.CREATING
                ListenerEventType.ASSIGNING -> EventType.ASSIGNING
                ListenerEventType.UPDATING -> EventType.UPDATING
                ListenerEventType.COMPLETING -> EventType.COMPLETING
                ListenerEventType.CANCELING -> EventType.CANCELING
                else -> return null
            }
            val task = job.userTask ?: return null
            val userTaskKey = task.userTaskKey ?: return null
            return UserTaskEvent(
                jobKey = job.key,
                type = type,
                userTaskKey = userTaskKey,
                elementInstanceKey = job.elementInstanceKey,
                processInstanceKey = job.processInstanceKey,
                processDefinitionKey = job.processDefinitionKey,
                processDefinitionId = job.bpmnProcessId,
                processDefinitionVersion = job.processDefinitionVersion,
                elementId = job.elementId,
                tenantId = job.tenantId,
                domain = job.customHeaders[TasklistApi.DOMAIN_HEADER]?.takeIf { it.isNotBlank() } ?: job.bpmnProcessId,
                businessId = job.businessId,
                subject = (job.variablesAsMap["subject"] as? String)?.take(SUBJECT_MAX_LENGTH),
                assignee = task.assignee?.takeIf { it.isNotBlank() },
                candidateGroups = task.candidateGroups.orEmpty(),
                candidateUsers = task.candidateUsers.orEmpty(),
                priority = task.priority ?: 50,
                dueDate = task.dueDate,
                followUpDate = task.followUpDate,
                action = task.action?.takeIf { it.isNotBlank() },
                changedAttributes = task.changedAttributes.orEmpty(),
            )
        }
    }
}

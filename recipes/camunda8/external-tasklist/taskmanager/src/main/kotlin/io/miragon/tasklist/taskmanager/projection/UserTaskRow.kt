package io.miragon.tasklist.taskmanager.projection

import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.contract.UserContext
import java.time.OffsetDateTime

/** A row of the task projection. Camunda owns the task; this is the tasklist's copy of it. */
data class UserTaskRow(
    val userTaskKey: Long,
    val elementInstanceKey: Long,
    val processInstanceKey: Long,
    val processDefinitionKey: Long,
    val processDefinitionId: String,
    val processDefinitionVersion: Int,
    val elementId: String,
    val tenantId: String,
    val name: String?,
    val processName: String?,
    val domain: String,
    val businessId: String?,
    val subject: String?,
    val formRef: String?,
    val state: TaskState,
    val assignee: String?,
    val candidateGroups: List<String>,
    val candidateUsers: List<String>,
    val priority: Int,
    val dueDate: OffsetDateTime?,
    val followUpDate: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val closedAt: OffsetDateTime?,
    val closedBy: String?,
    val lastJobKey: Long,
    val verifiedAt: OffsetDateTime,
) {
    val isOpen get() = state == TaskState.OPEN

    /** A candidate may claim and update the task; without any candidates everybody is one. */
    fun isCandidate(user: UserContext) =
        assignee == user.id ||
            user.id in candidateUsers ||
            candidateGroups.any { it in user.groups } ||
            (candidateGroups.isEmpty() && candidateUsers.isEmpty())

    fun toContext(user: UserContext, formRef: String) = TaskContext(
        userTaskKey = userTaskKey.toString(),
        processInstanceKey = processInstanceKey.toString(),
        processDefinitionKey = processDefinitionKey.toString(),
        processDefinitionId = processDefinitionId,
        processDefinitionVersion = processDefinitionVersion,
        elementId = elementId,
        formRef = formRef,
        businessId = businessId,
        state = state,
        assignee = assignee,
        candidateGroups = candidateGroups,
        tenantId = tenantId,
        user = user,
    )
}

data class HistoryRow(
    val eventType: String,
    val action: String?,
    val assignee: String?,
    val changedAttributes: List<String>,
    val source: String,
    val occurredAt: OffsetDateTime,
)

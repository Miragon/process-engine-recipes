package io.miragon.tasklist.taskmanager.projection

import io.miragon.tasklist.contract.TaskState
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
}

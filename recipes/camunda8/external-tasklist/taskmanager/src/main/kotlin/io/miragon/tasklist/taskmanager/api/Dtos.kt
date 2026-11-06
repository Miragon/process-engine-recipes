package io.miragon.tasklist.taskmanager.api

import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.contract.UserContext
import io.miragon.tasklist.taskmanager.projection.HistoryRow
import io.miragon.tasklist.taskmanager.projection.UserTaskRow
import java.time.OffsetDateTime

/** What the current user may do with a task right now; the tasklist shows its buttons accordingly. */
enum class TaskAction { CLAIM, UNCLAIM, UPDATE, COMPLETE }

data class TaskDto(
    val userTaskKey: String,
    val name: String,
    val processName: String?,
    val processDefinitionId: String,
    val processDefinitionVersion: Int,
    val processInstanceKey: String,
    val elementId: String,
    val domain: String,
    val domainLabel: String,
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
    val actions: Set<TaskAction>,
) {
    companion object {
        fun from(row: UserTaskRow, domainLabel: String, user: UserContext?) = TaskDto(
            userTaskKey = row.userTaskKey.toString(),
            name = row.name ?: row.elementId,
            processName = row.processName,
            processDefinitionId = row.processDefinitionId,
            processDefinitionVersion = row.processDefinitionVersion,
            processInstanceKey = row.processInstanceKey.toString(),
            elementId = row.elementId,
            domain = row.domain,
            domainLabel = domainLabel,
            businessId = row.businessId,
            subject = row.subject,
            formRef = row.formRef,
            state = row.state,
            assignee = row.assignee,
            candidateGroups = row.candidateGroups,
            candidateUsers = row.candidateUsers,
            priority = row.priority,
            dueDate = row.dueDate,
            followUpDate = row.followUpDate,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            closedAt = row.closedAt,
            closedBy = row.closedBy,
            actions = if (user == null) emptySet() else actionsFor(row, user),
        )

        private fun actionsFor(row: UserTaskRow, user: UserContext): Set<TaskAction> {
            if (!row.isOpen) return emptySet()
            return buildSet {
                if (row.isCandidate(user)) add(TaskAction.UPDATE)
                if (row.assignee == null && row.isCandidate(user)) add(TaskAction.CLAIM)
                if (row.assignee == user.id) {
                    add(TaskAction.UNCLAIM)
                    add(TaskAction.COMPLETE)
                }
            }
        }
    }
}

data class TaskPage(val items: List<TaskDto>, val total: Long, val from: Int, val limit: Int)

data class Facet(val value: String, val label: String, val count: Long)

data class Facets(
    /** `ANY`, `UNASSIGNED` and, when the user is known, `MINE`: the values of the `assignment` filter. */
    val assignments: List<Facet>,
    val domains: List<Facet>,
    val candidateGroups: List<Facet>,
    val processes: List<Facet>,
    val states: List<Facet>,
)

data class HistoryEntry(
    val eventType: String,
    val action: String?,
    val assignee: String?,
    val changedAttributes: List<String>,
    val source: String,
    val occurredAt: OffsetDateTime,
) {
    companion object {
        fun from(row: HistoryRow) =
            HistoryEntry(row.eventType, row.action, row.assignee, row.changedAttributes, row.source, row.occurredAt)
    }
}

/** Fields of a task the tasklist may change; omitted fields stay as they are. */
data class TaskUpdate(
    val priority: Int? = null,
    val dueDate: OffsetDateTime? = null,
    val candidateGroups: List<String>? = null,
)

data class SubmissionBody(val data: Map<String, Any?> = emptyMap())

data class StartFormEntry(
    val domain: String,
    val domainLabel: String,
    val id: String,
    val formRef: String,
    val label: String,
    val description: String?,
)

data class ApplicationDto(val domain: String, val label: String)

data class TaskChangedDto(val userTaskKey: String, val state: TaskState, val domain: String)

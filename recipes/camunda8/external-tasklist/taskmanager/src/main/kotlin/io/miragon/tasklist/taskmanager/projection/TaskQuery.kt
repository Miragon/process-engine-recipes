package io.miragon.tasklist.taskmanager.projection

import io.miragon.tasklist.contract.TaskState
import java.time.OffsetDateTime

/** Filters of the task list. `states == null` means all states. */
data class TaskQuery(
    val states: List<TaskState>? = listOf(TaskState.OPEN),
    val domain: String? = null,
    val assignment: Assignment = Assignment.ANY,
    val userId: String? = null,
    val candidateGroup: String? = null,
    val processDefinitionId: String? = null,
    val priorityMin: Int? = null,
    val dueBefore: OffsetDateTime? = null,
    val q: String? = null,
    val sort: Sort = Sort.CREATED_AT,
    val descending: Boolean? = null,
    val from: Int = 0,
    val limit: Int = 50,
) {
    enum class Assignment { MINE, UNASSIGNED, ANY }

    enum class Sort(val column: String, val descendingByDefault: Boolean) {
        CREATED_AT("created_at", true),
        PRIORITY("priority", true),
        DUE_DATE("due_date", false),
    }

    fun orderBy(): String {
        val direction = if (descending ?: sort.descendingByDefault) "DESC" else "ASC"
        return "${sort.column} $direction NULLS LAST, user_task_key DESC"
    }
}

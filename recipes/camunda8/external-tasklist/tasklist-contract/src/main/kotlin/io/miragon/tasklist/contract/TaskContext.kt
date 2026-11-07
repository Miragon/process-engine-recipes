package io.miragon.tasklist.contract

/**
 * What the task manager tells a process application about a user task.
 *
 * The task manager builds it from its own projection and the signed-in user, never from the browser.
 * Keys are strings because Camunda keys exceed the integer range of JavaScript.
 */
data class TaskContext(
    val userTaskKey: String,
    val processInstanceKey: String,
    val processDefinitionKey: String,
    val processDefinitionId: String,
    val processDefinitionVersion: Int,
    val elementId: String,
    val formRef: String,
    val businessId: String?,
    val state: TaskState,
    val assignee: String?,
    val candidateGroups: List<String>,
    val tenantId: String,
    val user: UserContext,
)

data class UserContext(
    val id: String,
    val groups: List<String>,
)

enum class TaskState { OPEN, COMPLETED, CANCELED }

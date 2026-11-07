package io.miragon.tasklist.taskmanager.api

/**
 * Claim, unclaim and update, sent to the engine. Each command returns only after the user task listeners ran, the
 * task manager's own one included, so the projection is current when it returns.
 *
 * Each returns false if the engine accepted the command but did not confirm it in time. It throws [TaskNotActive] when
 * the task is gone, and an [ApiProblem] when a listener denied the command (422) or it conflicts with the task (409).
 */
interface TaskCommands {
    fun assign(userTaskKey: Long, assignee: String): Boolean

    fun unassign(userTaskKey: Long): Boolean

    fun update(userTaskKey: Long, update: TaskUpdate): Boolean
}

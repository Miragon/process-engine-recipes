package io.miragon.tasklist.taskmanager.api

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientHttpException
import io.camunda.client.api.command.ProblemException
import org.springframework.stereotype.Component

/**
 * Claim, unclaim and update go straight to Camunda. Each command returns only after the user task listeners ran,
 * the task manager's own one included, so the projection is current when it returns.
 */
@Component
class TaskCommands(private val camunda: CamundaClient) {

    /** Returns false if Camunda accepted the command but the listeners did not finish within its 15 s timeout. */
    fun assign(userTaskKey: Long, assignee: String): Boolean = run {
        camunda.newAssignUserTaskCommand(userTaskKey).assignee(assignee).allowOverride(false).action("claim").send().join()
    }

    fun unassign(userTaskKey: Long): Boolean = run {
        camunda.newUnassignUserTaskCommand(userTaskKey).send().join()
    }

    fun update(userTaskKey: Long, update: TaskUpdate): Boolean = run {
        val command = camunda.newUpdateUserTaskCommand(userTaskKey).action("update")
        update.priority?.let { command.priority(it) }
        update.dueDate?.let { command.dueDate(it) }
        update.candidateGroups?.let { command.candidateGroups(it) }
        command.send().join()
    }

    private fun run(command: () -> Any): Boolean =
        try {
            command()
            true
        } catch (e: ClientHttpException) {
            val detail = (e as? ProblemException)?.details()?.detail ?: e.message.orEmpty()
            when (e.code()) {
                404 -> throw TaskNotActive()
                409 -> throw DENIAL.find(detail)?.let { ApiProblem(422, "DENIED", it.groupValues[1]) }
                    ?: ApiProblem(409, "CONFLICT", detail)
                504 -> false
                else -> throw e
            }
        }

    private companion object {
        val DENIAL = Regex("""Reason to deny: '(.*)'""", RegexOption.DOT_MATCHES_ALL)
    }
}

package io.miragon.tasklist.taskmanager.camunda

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientHttpException
import io.miragon.tasklist.contract.provider.denialReason
import io.miragon.tasklist.contract.provider.problemDetail
import io.miragon.tasklist.taskmanager.api.ApiProblem
import io.miragon.tasklist.taskmanager.api.TaskCommands
import io.miragon.tasklist.taskmanager.api.TaskNotActive
import io.miragon.tasklist.taskmanager.api.TaskUpdate
import org.springframework.stereotype.Component

/**
 * The task commands on Camunda's REST API, which answers only after the user task listeners ran. A claim never takes
 * a task over from someone else. A command is unconfirmed when the listeners did not finish within Camunda's 15 s.
 */
@Component
class CamundaTaskCommands(private val camunda: CamundaClient) : TaskCommands {

    override fun assign(userTaskKey: Long, assignee: String): Boolean = run {
        camunda.newAssignUserTaskCommand(userTaskKey).assignee(assignee).allowOverride(false).action("claim").send().join()
    }

    override fun unassign(userTaskKey: Long): Boolean = run {
        camunda.newUnassignUserTaskCommand(userTaskKey).send().join()
    }

    override fun update(userTaskKey: Long, update: TaskUpdate): Boolean = run {
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
            when (e.code()) {
                404 -> throw TaskNotActive()
                409 -> throw e.denialReason()?.let { ApiProblem(422, "DENIED", it) } ?: ApiProblem(409, "CONFLICT", e.problemDetail())
                504 -> false
                else -> throw e
            }
        }
}

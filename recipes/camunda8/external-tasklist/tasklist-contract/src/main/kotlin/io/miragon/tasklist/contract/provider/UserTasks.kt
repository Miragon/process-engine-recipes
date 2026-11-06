package io.miragon.tasklist.contract.provider

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientHttpException
import io.camunda.client.api.command.ProblemException
import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.TaskNotActive
import io.miragon.tasklist.contract.ValidationFailed

private val DENIAL = Regex("""Reason to deny: '(.*)'""", RegexOption.DOT_MATCHES_ALL)

/**
 * Completes a user task and translates Camunda's answer into the tasklist contract.
 *
 * Call it inside the transaction that stores the domain change, so a rejected completion rolls it back:
 * - 404: the task is gone (completed, canceled by an event or by its process) → [TaskNotActive]
 * - 409 denied by a completing listener → [ValidationFailed] with the listener's reason
 * - 504: the listeners did not answer in time; Camunda still completes the task later → [CompletionOutcome.PENDING]
 */
fun CamundaClient.completeUserTask(userTaskKey: String, variables: Map<String, Any?> = emptyMap()): CompletionOutcome =
    try {
        newCompleteUserTaskCommand(userTaskKey.toLong()).variables(variables).send().join()
        CompletionOutcome.COMPLETED
    } catch (e: ClientHttpException) {
        when (e.code()) {
            404 -> throw TaskNotActive()
            409 -> {
                val detail = (e as? ProblemException)?.details()?.detail.orEmpty()
                val reason = DENIAL.find(detail)?.groupValues?.get(1)
                throw if (reason != null) ValidationFailed(reason) else TaskNotActive()
            }
            504 -> CompletionOutcome.PENDING
            else -> throw e
        }
    }

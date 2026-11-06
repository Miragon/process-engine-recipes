package io.miragon.tasklist.contract.provider

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientException
import io.camunda.client.api.command.ClientHttpException
import io.camunda.client.api.command.ProblemException
import io.miragon.tasklist.contract.CamundaUnavailable
import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.TaskNotActive
import io.miragon.tasklist.contract.ValidationFailed
import org.apache.hc.client5.http.ConnectTimeoutException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

private val DENIAL = Regex("""Reason to deny: '(.*)'""", RegexOption.DOT_MATCHES_ALL)

/**
 * Completes a user task and translates Camunda's answer into the tasklist contract.
 *
 * Call it inside the transaction that stores the domain change, so a rejected completion rolls it back:
 * - 404: the task is gone (completed, canceled by an event or by its process) → [TaskNotActive]
 * - 409 denied by a completing listener → [ValidationFailed] with the listener's reason
 * - Camunda not reachable (refused, unknown host, no route, connect timeout) → [CamundaUnavailable]; the request
 *   never left, so nothing happened and the change rolls back
 * - 504, or no answer after the request went out: Camunda may still complete the task → [CompletionOutcome.PENDING]
 */
fun CamundaClient.completeUserTask(userTaskKey: String, variables: Map<String, Any?> = emptyMap()): CompletionOutcome =
    try {
        newCompleteUserTaskCommand(userTaskKey.toLong()).variables(variables).send().join()
        CompletionOutcome.COMPLETED
    } catch (e: ClientException) {
        completionOutcome(e)
    }

/** What a failed user task command means for the tasklist; throws for the answers that are errors. */
internal fun completionOutcome(e: ClientException): CompletionOutcome {
    if (e !is ClientHttpException) {
        // Only a failure before the request left proves that Camunda never saw it; after that it may complete the task.
        if (e.causes().any { it.beforeSending() }) throw CamundaUnavailable()
        return CompletionOutcome.PENDING
    }
    return when (e.code()) {
        404 -> throw TaskNotActive()
        409 -> throw e.denialReason()?.let { ValidationFailed(it) } ?: TaskNotActive()
        504 -> CompletionOutcome.PENDING
        else -> throw e
    }
}

/** The reason a user task listener gave when it denied a command, from Camunda's 409 answer. */
fun ClientHttpException.denialReason(): String? =
    DENIAL.find(problemDetail())?.groupValues?.get(1)

/** The detail of Camunda's problem answer, or the plain message. */
fun ClientHttpException.problemDetail(): String =
    (this as? ProblemException)?.details()?.detail ?: message.orEmpty()

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }

private fun Throwable.beforeSending() =
    this is ConnectException || this is UnknownHostException || this is NoRouteToHostException || this is ConnectTimeoutException

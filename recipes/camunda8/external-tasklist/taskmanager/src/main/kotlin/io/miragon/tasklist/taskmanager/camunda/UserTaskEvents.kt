package io.miragon.tasklist.taskmanager.camunda

import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.search.enums.JobKind
import io.camunda.client.api.search.enums.ListenerEventType
import io.miragon.tasklist.contract.TasklistApi
import io.miragon.tasklist.taskmanager.projection.UserTaskEvent
import io.miragon.tasklist.taskmanager.projection.UserTaskEvent.EventType

private const val SUBJECT_MAX_LENGTH = 500

/** Reads a task listener job; returns null for anything else, which the worker just completes. */
internal fun ActivatedJob.toUserTaskEvent(): UserTaskEvent? {
    if (kind != JobKind.TASK_LISTENER) return null
    val type = when (listenerEventType) {
        ListenerEventType.CREATING -> EventType.CREATING
        ListenerEventType.ASSIGNING -> EventType.ASSIGNING
        ListenerEventType.UPDATING -> EventType.UPDATING
        ListenerEventType.COMPLETING -> EventType.COMPLETING
        ListenerEventType.CANCELING -> EventType.CANCELING
        else -> return null
    }
    val task = userTask ?: return null
    val userTaskKey = task.userTaskKey ?: return null
    return UserTaskEvent(
        jobKey = key,
        type = type,
        userTaskKey = userTaskKey,
        elementInstanceKey = elementInstanceKey,
        processInstanceKey = processInstanceKey,
        processDefinitionKey = processDefinitionKey,
        processDefinitionId = bpmnProcessId,
        processDefinitionVersion = processDefinitionVersion,
        elementId = elementId,
        tenantId = tenantId,
        domain = customHeaders[TasklistApi.DOMAIN_HEADER]?.takeIf { it.isNotBlank() } ?: bpmnProcessId,
        businessId = businessId,
        subject = (variablesAsMap["subject"] as? String)?.take(SUBJECT_MAX_LENGTH),
        assignee = task.assignee?.takeIf { it.isNotBlank() },
        candidateGroups = task.candidateGroups.orEmpty(),
        candidateUsers = task.candidateUsers.orEmpty(),
        priority = task.priority ?: 50,
        dueDate = task.dueDate,
        followUpDate = task.followUpDate,
        action = task.action?.takeIf { it.isNotBlank() },
        changedAttributes = task.changedAttributes.orEmpty(),
    )
}

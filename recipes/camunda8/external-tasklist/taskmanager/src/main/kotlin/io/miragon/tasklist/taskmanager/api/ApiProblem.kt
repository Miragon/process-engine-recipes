package io.miragon.tasklist.taskmanager.api

import io.miragon.tasklist.contract.FieldError

/** An error of the task manager API, rendered as `application/problem+json` with a `code`. */
open class ApiProblem(
    val status: Int,
    val code: String,
    override val message: String,
    val errors: List<FieldError> = emptyList(),
    val retryAfterSeconds: Int? = null,
) : RuntimeException(message)

class TaskNotFound(userTaskKey: Long) :
    ApiProblem(404, "NOT_FOUND", "There is no task $userTaskKey.")

class NotAllowed(message: String) :
    ApiProblem(403, "FORBIDDEN", message)

class TaskNotActive(message: String = "The task is no longer active.") :
    ApiProblem(409, "TASK_NOT_ACTIVE", message)

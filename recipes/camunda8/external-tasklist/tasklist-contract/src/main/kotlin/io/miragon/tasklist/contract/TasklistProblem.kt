package io.miragon.tasklist.contract

/**
 * Errors a process application reports to the task manager.
 *
 * They travel as `application/problem+json` with the extra properties `code` and `errors`,
 * and the task manager passes them on to the tasklist unchanged.
 */
sealed class TasklistProblem(
    val status: Int,
    val code: String,
    override val message: String,
    val errors: List<FieldError> = emptyList(),
) : RuntimeException(message)

data class FieldError(val field: String, val message: String)

class ValidationFailed(message: String, errors: List<FieldError> = emptyList()) :
    TasklistProblem(422, "VALIDATION_FAILED", message, errors) {
    constructor(field: String, message: String) : this(message, listOf(FieldError(field, message)))
}

class TaskNotActive(message: String = "The task is no longer active.") :
    TasklistProblem(409, "TASK_NOT_ACTIVE", message)

class DomainConflict(message: String) :
    TasklistProblem(409, "CONFLICT", message)

class FormNotFound(formRef: String) :
    TasklistProblem(404, "FORM_NOT_FOUND", "This application does not know the form '$formRef'.")

class DraftNotSupported(formRef: String) :
    TasklistProblem(405, "DRAFT_NOT_SUPPORTED", "The form '$formRef' does not keep drafts.")

class BadTasklistRequest(message: String) :
    TasklistProblem(400, "BAD_REQUEST", message)

class CamundaUnavailable(message: String = "Camunda is not reachable. Nothing was changed; try again in a moment.") :
    TasklistProblem(503, "UNAVAILABLE", message)

package io.miragon.tasklist.contract.provider

import io.miragon.tasklist.contract.TasklistProblem
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [TasklistProviderController::class])
class TasklistProviderExceptionHandler {

    @ExceptionHandler(TasklistProblem::class)
    fun handle(problem: TasklistProblem): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(problem.status), problem.message).apply {
            setProperty("code", problem.code)
            if (problem.errors.isNotEmpty()) setProperty("errors", problem.errors)
        }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(e: HttpMessageNotReadableException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request body is not a valid tasklist request.").apply {
            setProperty("code", "BAD_REQUEST")
        }
}

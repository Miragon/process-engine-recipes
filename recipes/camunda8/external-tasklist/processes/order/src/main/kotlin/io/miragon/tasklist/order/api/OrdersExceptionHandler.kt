package io.miragon.tasklist.order.api

import io.miragon.tasklist.contract.TasklistProblem
import io.miragon.tasklist.order.orders.OrderNotFound
import io.miragon.tasklist.order.payment.ProcessNotWaiting
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Answers errors of the orders API as `application/problem+json` with a `code`, like the provider API does. */
@RestControllerAdvice(assignableTypes = [OrdersController::class])
class OrdersExceptionHandler {

    @ExceptionHandler(TasklistProblem::class)
    fun problem(problem: TasklistProblem): ProblemDetail =
        problemDetail(HttpStatus.valueOf(problem.status), problem.code, problem.message).apply {
            if (problem.errors.isNotEmpty()) setProperty("errors", problem.errors)
        }

    @ExceptionHandler(OrderNotFound::class)
    fun notFound(e: OrderNotFound): ProblemDetail = problemDetail(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", e.message)

    @ExceptionHandler(ProcessNotWaiting::class)
    fun notWaiting(e: ProcessNotWaiting): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
            .body(problemDetail(HttpStatus.SERVICE_UNAVAILABLE, "PROCESS_NOT_WAITING", e.message))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(e: HttpMessageNotReadableException): ProblemDetail =
        problemDetail(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request body is missing or does not match the API.")

    private fun problemDetail(status: HttpStatus, code: String, detail: String?) =
        ProblemDetail.forStatusAndDetail(status, detail).apply { setProperty("code", code) }

    private companion object {
        const val RETRY_AFTER_SECONDS = "2"
    }
}

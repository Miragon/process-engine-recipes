package io.miragon.tasklist.taskmanager.api

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice(basePackageClasses = [ApiExceptionHandler::class])
class ApiExceptionHandler {

    @ExceptionHandler(ApiProblem::class)
    fun handle(problem: ApiProblem): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(problem.status), problem.message).apply {
            setProperty("code", problem.code)
            if (problem.errors.isNotEmpty()) setProperty("errors", problem.errors)
        }
        return ResponseEntity.status(problem.status)
            .apply { problem.retryAfterSeconds?.let { header(HttpHeaders.RETRY_AFTER, it.toString()) } }
            .body(body)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, HttpMessageNotReadableException::class)
    fun badRequest(e: Exception): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is not valid: ${e.message?.lineSequence()?.first()}").apply {
            setProperty("code", "BAD_REQUEST")
        }
}

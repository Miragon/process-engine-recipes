package io.miragon.tasklist.payment.api

import io.miragon.tasklist.payment.engine.ProcessEngineRefused
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/** Answers every error of the payments API as `application/problem+json`, including malformed requests. */
@RestControllerAdvice(assignableTypes = [PaymentController::class])
class PaymentApiExceptionHandler : ResponseEntityExceptionHandler() {

    /** The transaction of the request rolled back, so the caller may simply try again. */
    @ExceptionHandler(ProcessEngineRefused::class)
    fun camundaUnavailable(e: ProcessEngineRefused): ProblemDetail {
        logger.warn("Camunda did not accept the start of a payment process", e)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Camunda did not accept the start of the payment process. Try again later.",
        )
    }
}

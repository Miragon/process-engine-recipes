package io.miragon.tasklist.contract.provider

import io.camunda.client.api.ProblemDetail
import io.camunda.client.api.command.ClientException
import io.camunda.client.api.command.ProblemException
import io.miragon.tasklist.contract.CamundaUnavailable
import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.TaskNotActive
import io.miragon.tasklist.contract.ValidationFailed
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.apache.hc.client5.http.ConnectTimeoutException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

class UserTasksTest {

    @Test
    fun `a task that is gone or denied is a problem of the form`() {
        assertThatThrownBy { completionOutcome(problem(404, "no such user task")) }.isInstanceOf(TaskNotActive::class.java)
        assertThatThrownBy { completionOutcome(problem(409, "Command 'COMPLETE' rejected with code 'INVALID_STATE': it is being canceled")) }
            .isInstanceOf(TaskNotActive::class.java)
        assertThatThrownBy {
            completionOutcome(problem(409, "Completion of the User Task with key '1' was denied by Task Listener. Reason to deny: 'Amount missing'"))
        }
            .isInstanceOf(ValidationFailed::class.java)
            .hasMessage("Amount missing")
    }

    @Test
    fun `an answer that never came may still complete the task`() {
        assertThat(completionOutcome(problem(504, "request timed out between gateway and broker"))).isEqualTo(CompletionOutcome.PENDING)
        assertThat(completionOutcome(ClientException("timed out", TimeoutException()))).isEqualTo(CompletionOutcome.PENDING)
        assertThat(completionOutcome(ClientException("read timed out", SocketTimeoutException()))).isEqualTo(CompletionOutcome.PENDING)
    }

    @Test
    fun `an unreachable Camunda changed nothing`() {
        for (cause in listOf(ConnectException(), UnknownHostException("camunda"), NoRouteToHostException(), ConnectTimeoutException("connect timed out"))) {
            assertThatThrownBy { completionOutcome(ClientException("not sent", cause)) }
                .describedAs(cause.javaClass.simpleName)
                .isInstanceOf(CamundaUnavailable::class.java)
        }
    }

    @Test
    fun `other errors stay errors`() {
        val error = problem(500, "boom")
        assertThatThrownBy { completionOutcome(error) }.isSameAs(error)
    }

    private fun problem(status: Int, detail: String) =
        ProblemException(status, "HTTP $status", ProblemDetail().setStatus(status).setDetail(detail))
}

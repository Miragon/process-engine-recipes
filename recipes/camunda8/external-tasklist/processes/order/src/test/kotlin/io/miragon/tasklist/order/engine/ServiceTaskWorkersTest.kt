package io.miragon.tasklist.order.engine

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.adapter.c8.task.subscription.C8TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CompletableFuture

class ServiceTaskWorkersTest {

    private val subscriptions = InMemSubscriptionRepository()
    private val answers = mutableListOf<Any>()
    private var engineDown = false
    private val workers = ServiceTaskWorkers(C8TaskSubscriptionApiImpl(subscriptions, null), object : ServiceTaskCompletionApi {
        override fun completeTask(cmd: CompleteTaskCmd) = answer(cmd)
        override fun completeTaskByError(cmd: CompleteTaskByErrorCmd) = answer(cmd)
        override fun failTask(cmd: FailTaskCmd) = answer(cmd)
    })

    @Test
    fun `completes the job with the variables the worker returns`() {
        workers.register("ship", setOf("orderId")) { mapOf("shipped" to it.string("orderId")) }

        deliver(retries = 3, "orderId" to "ORD-1001", type = "ship")

        val completion = answers.single() as CompleteTaskCmd
        assertThat(completion.taskId).isEqualTo("42")
        assertThat(completion.get()).isEqualTo(mapOf("shipped" to "ORD-1001"))
    }

    @Test
    fun `fails the job the way the worker asks`() {
        workers.register("needs-a-person", emptySet()) { throw IncidentRequired("Refused with 409") }
        workers.register("busy", emptySet()) { throw RetryLater("Answered 503", Duration.ofSeconds(4)) }
        workers.register("broken", emptySet()) { error("Order ORD-1001 is gone.") }

        for (type in listOf("needs-a-person", "busy", "broken")) deliver(retries = 3, type = type)

        val (incident, retry, unexpected) = answers.map { it as FailTaskCmd }
        assertThat(listOf(incident.reason, incident.retryCount, incident.retryBackoff)).containsExactly("Refused with 409", 0, Duration.ZERO)
        assertThat(listOf(retry.reason, retry.retryCount, retry.retryBackoff)).containsExactly("Answered 503", 2, Duration.ofSeconds(4))
        assertThat(unexpected.reason).startsWith("java.lang.IllegalStateException: Order ORD-1001 is gone.\n\tat ")
        assertThat(listOf(unexpected.retryCount, unexpected.retryBackoff)).containsExactly(2, Duration.ZERO)
    }

    @Test
    fun `leaves a job it cannot answer to its timeout`() {
        workers.register("ship", emptySet()) { emptyMap() }
        engineDown = true

        assertThatCode { deliver(retries = 3, type = "ship") }.doesNotThrowAnyException()
    }

    /** Hands a job to the worker the way the adapter does: with its retries in the meta and the variables asked for. */
    private fun deliver(retries: Int, vararg variables: Pair<String, Any?>, type: String) =
        subscriptions.getTaskSubscriptions().single { it.taskDescriptionKey == type }
            .action.accept(TaskInformation("42", mapOf(TaskInformation.RETRIES to "$retries")), mapOf(*variables))

    private fun answer(cmd: Any): CompletableFuture<Empty> {
        check(!engineDown) { "Camunda is not reachable" }
        answers += cmd
        return CompletableFuture.completedFuture(Empty)
    }
}

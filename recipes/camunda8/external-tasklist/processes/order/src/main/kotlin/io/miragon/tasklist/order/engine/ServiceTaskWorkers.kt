package io.miragon.tasklist.order.engine

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskHandler
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.TaskTerminationHandler
import dev.bpmcrafters.processengineapi.task.TaskType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.CompletableFuture

/** A job of a service task, with the variables its worker asked for. */
class ServiceTask(val type: String, private val information: TaskInformation, private val variables: Map<String, Any?>) {
    val id: String get() = information.taskId

    /** The attempts left, this one included. */
    val retries: Int get() = checkNotNull(information.getMetaValueAsInt(TaskInformation.RETRIES)) { "Job $id has no retries." }

    fun string(name: String): String = checkNotNull(stringOrNull(name)) { "Job $id has no variable $name." }

    fun stringOrNull(name: String): String? = variables[name] as String?
}

/** The job needs a person: it fails without retries, and the engine raises an incident with this message. */
class IncidentRequired(message: String) : RuntimeException(message)

/** The job fails with one retry less and runs again after [backoff]. */
class RetryLater(message: String, val backoff: Duration) : RuntimeException(message)

/**
 * Runs the workers of service tasks on the Process Engine API and answers their jobs the way Camunda's `@JobWorker`
 * does: the returned variables complete the job, [IncidentRequired] fails it without retries, [RetryLater] with one
 * retry less after its backoff, and anything else it throws with one retry less right away. The message, or the stack
 * trace of an unexpected failure, becomes the error message an incident shows.
 */
@Component
class ServiceTaskWorkers(
    private val subscriptions: TaskSubscriptionApi,
    private val jobs: ServiceTaskCompletionApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Register while the application starts: the engine adapter opens the workers once its client is up. */
    fun register(jobType: String, variables: Set<String>, worker: (ServiceTask) -> Map<String, Any?>) {
        subscriptions.subscribeForTask(
            SubscribeForTaskCmd(
                restrictions = emptyMap(),
                taskType = TaskType.EXTERNAL,
                taskDescriptionKey = jobType,
                payloadDescription = variables,
                action = TaskHandler { information, payload -> answer(ServiceTask(jobType, information, payload), worker) },
                termination = TaskTerminationHandler {},
            ),
        ).join()
    }

    /** Answers every job, also when its worker fails with an `Error` such as Kotlin's `TODO()`. */
    private fun answer(job: ServiceTask, worker: (ServiceTask) -> Map<String, Any?>) {
        val variables = try {
            worker(job)
        } catch (e: Throwable) {
            send(job) { jobs.failTask(failure(job, e)) }
            return
        }
        send(job) { jobs.completeTask(CompleteTaskCmd(job.id, variables)) }
    }

    private fun failure(job: ServiceTask, e: Throwable): FailTaskCmd = when (e) {
        is IncidentRequired -> FailTaskCmd(job.id, e.message.orEmpty(), null, 0, Duration.ZERO)
        is RetryLater -> FailTaskCmd(job.id, e.message.orEmpty(), null, job.retries - 1, e.backoff)
        else -> {
            log.warn("Job {} of type {} failed", job.id, job.type, e)
            FailTaskCmd(job.id, e.stackTraceToString(), null, job.retries - 1, Duration.ZERO)
        }
    }

    /** As with Camunda's job workers, an answer the engine does not take is only logged; the job comes back after its timeout. */
    private fun send(job: ServiceTask, command: () -> CompletableFuture<Empty>) {
        try {
            command().join()
        } catch (e: Exception) {
            log.warn("Could not answer job {} of type {}", job.id, job.type, e)
        }
    }
}

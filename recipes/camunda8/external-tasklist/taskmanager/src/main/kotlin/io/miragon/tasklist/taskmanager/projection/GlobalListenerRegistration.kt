package io.miragon.tasklist.taskmanager.projection

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientHttpException
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType
import io.miragon.tasklist.taskmanager.TaskmanagerProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Registers the task manager's global user task listener in the cluster, so no process model has to declare it.
 * It runs after model-level listeners and with the lowest priority, so it sees the final state of each transition.
 * The registration stays when the task manager stops: user tasks then wait for it instead of being missed.
 */
@Component("globalListener")
class GlobalListenerRegistration(
    private val camunda: CamundaClient,
    private val properties: TaskmanagerProperties,
) : HealthIndicator {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var registered = false

    @Volatile
    private var lastError: String? = null

    @EventListener(ApplicationReadyEvent::class)
    fun register() {
        Thread.ofVirtual().name("global-listener-registration").start(::registerUntilDone)
    }

    override fun health(): Health =
        if (registered) {
            Health.up().withDetail("id", properties.listener.id).withDetail("type", properties.listener.type).build()
        } else {
            Health.down().withDetail("reason", lastError ?: "not registered yet").build()
        }

    private fun registerUntilDone() {
        var delay = Duration.ofSeconds(1)
        while (!registered) {
            try {
                registerNow()
            } catch (e: RuntimeException) {
                lastError = e.message
                log.warn("Registering the global user task listener failed, retrying in {}: {}", delay, e.message)
                Thread.sleep(delay)
                delay = minOf(delay.multipliedBy(2), Duration.ofSeconds(15))
            }
        }
    }

    /** Creates the listener, or updates it if it exists, so its settings always match this configuration. */
    fun registerNow() {
        val listener = properties.listener
        try {
            camunda.newCreateGlobalTaskListenerRequest()
                .id(listener.id)
                .type(listener.type)
                .eventType(GlobalTaskListenerEventType.ALL)
                .afterNonGlobal(true)
                .priority(listener.priority)
                .retries(listener.retries)
                .send()
                .join()
        } catch (e: ClientHttpException) {
            if (e.code() != 409) throw e
            camunda.newUpdateGlobalTaskListenerRequest(listener.id)
                .type(listener.type)
                .eventType(GlobalTaskListenerEventType.ALL)
                .afterNonGlobal(true)
                .priority(listener.priority)
                .retries(listener.retries)
                .send()
                .join()
        }
        registered = true
        log.info("Global user task listener '{}' registered for job type '{}'", listener.id, listener.type)
    }
}

package io.miragon.tasklist.taskmanager.projection

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Names and form references for the listener handler without calling Camunda on its path: the handler takes what
 * the cache knows. For a process definition it has not seen yet, the BPMN is loaded in the background and the
 * tasks of that definition are filled in, which also reaches the tasklist as a change event.
 */
@Component
class TaskDetailsResolver(
    private val definitions: ProcessDefinitions,
    private val tasks: TaskRepository,
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val loading = ConcurrentHashMap.newKeySet<Long>()
    private val loader = Executors.newVirtualThreadPerTaskExecutor()

    fun cachedOrLoadLater(event: UserTaskEvent): TaskDetails? =
        definitions.cachedTask(event.processDefinitionKey, event.elementId)
            ?: null.also { loadLater(event.processDefinitionKey, event.processDefinitionId) }

    @PreDestroy
    fun close() = loader.shutdownNow()

    private fun loadLater(definitionKey: Long, processId: String) {
        if (!loading.add(definitionKey)) return
        loader.execute {
            try {
                var delay = Duration.ofSeconds(1)
                for (attempt in 1..ATTEMPTS) {
                    val details = definitions.load(definitionKey, processId)
                    if (details != null) {
                        tasks.fillDetailsOfDefinition(definitionKey, details).forEach(events::publishEvent)
                        return@execute
                    }
                    Thread.sleep(delay)
                    delay = delay.multipliedBy(2)
                }
                log.info("Process definition {} still unknown to Camunda's search; the reconciler fills in its tasks", definitionKey)
            } finally {
                loading.remove(definitionKey)
            }
        }
    }

    private companion object {
        /** 1 + 2 + 4 + 8 + 16 s; after that the reconciler takes over. */
        const val ATTEMPTS = 5
    }
}

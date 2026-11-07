package io.miragon.tasklist.taskmanager.projection

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Names and form references of user tasks, read once per process definition from its model.
 * A deployed definition never changes, so the cache never expires.
 */
@Component
class ProcessDefinitions(private val models: ProcessModels) {

    private val cache = ConcurrentHashMap<Long, Map<String, TaskDetails>>()

    /** Details from the cache only; never calls the engine, so the listener handler can use it. */
    fun cachedTask(processDefinitionKey: Long, elementId: String): TaskDetails? = cache[processDefinitionKey]?.get(elementId)

    /** Details of a task, loading the definition if needed; null while the engine does not know it yet. */
    fun task(processDefinitionKey: Long, processDefinitionId: String, elementId: String): TaskDetails? =
        load(processDefinitionKey, processDefinitionId)?.get(elementId)

    /** All user tasks of a definition by element ID, from the cache or the engine; null while the engine does not know it. */
    fun load(key: Long, processId: String): Map<String, TaskDetails>? =
        cache[key] ?: models.userTasks(key, processId)?.also { cache[key] = it }
}

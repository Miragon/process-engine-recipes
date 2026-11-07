package io.miragon.tasklist.taskmanager.api

import io.miragon.tasklist.taskmanager.projection.EngineUserTasks
import io.miragon.tasklist.taskmanager.projection.ProcessDefinitions
import io.miragon.tasklist.taskmanager.projection.TaskRepository
import io.miragon.tasklist.taskmanager.projection.UserTaskRow
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Finds the form reference of a task. Usually the listener already stored it from the BPMN model;
 * otherwise it is looked up in the model or, for a FEEL expression, in Camunda's eventually consistent search.
 */
@Component
class FormRefs(
    private val tasks: TaskRepository,
    private val definitions: ProcessDefinitions,
    private val engineTasks: EngineUserTasks,
) {
    fun of(task: UserTaskRow): String =
        task.formRef ?: resolve(task)
            ?: throw ApiProblem(503, "SYNCING", "The task is still being synchronized with Camunda. Try again in a moment.", retryAfterSeconds = 2)

    private fun resolve(task: UserTaskRow): String? {
        definitions.task(task.processDefinitionKey, task.processDefinitionId, task.elementId)
            ?.takeIf { it.formRef != null }
            ?.let { tasks.fillDetails(task.userTaskKey, it); return it.formRef }
        repeat(3) { attempt ->
            engineTasks.find(task.userTaskKey)?.details?.takeIf { it.formRef != null }?.let {
                tasks.fillDetails(task.userTaskKey, it)
                return it.formRef
            }
            Thread.sleep(Duration.ofMillis(500L * (attempt + 1)))
        }
        return null
    }
}

package io.miragon.tasklist.taskmanager.projection

import io.miragon.tasklist.contract.TaskState
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/** Published after a projection change has been committed; the tasklist receives it as a server-sent event. */
data class TaskChanged(val userTaskKey: Long, val state: TaskState, val domain: String)

@Service
class ProjectionService(
    private val tasks: TaskRepository,
    private val jdbc: JdbcClient,
    private val events: ApplicationEventPublisher,
) {
    /**
     * Applies one listener event. Runs while Camunda holds the user task in a transitional state,
     * so it only touches the database and gives up quickly instead of blocking the task.
     */
    @Transactional
    fun apply(event: UserTaskEvent, details: TaskDetails?, now: OffsetDateTime = OffsetDateTime.now()): Boolean {
        jdbc.sql("SET LOCAL statement_timeout = '2s'").update()
        if (!tasks.upsert(event, details, now)) return false
        tasks.insertEvent(
            userTaskKey = event.userTaskKey,
            jobKey = event.jobKey,
            eventType = event.type.name,
            action = event.action,
            assignee = event.assignee,
            changedAttributes = event.changedAttributes,
            source = "LISTENER",
            now = now,
        )
        events.publishEvent(TaskChanged(event.userTaskKey, event.type.state, event.domain))
        return true
    }

    @Transactional
    fun closeByReconciler(task: UserTaskRow, state: TaskState, now: OffsetDateTime = OffsetDateTime.now()): Boolean {
        if (!tasks.closeByReconciler(task.userTaskKey, state, task.lastJobKey, now)) return false
        val eventType = if (state == TaskState.COMPLETED) "COMPLETED" else "CANCELED"
        tasks.insertEvent(task.userTaskKey, null, eventType, null, task.assignee, emptyList(), "RECONCILER", now)
        events.publishEvent(TaskChanged(task.userTaskKey, state, task.domain))
        return true
    }

    @Transactional
    fun insertMissing(task: CamundaTask, now: OffsetDateTime = OffsetDateTime.now()): Boolean {
        if (!tasks.insertFromCamunda(task, now)) return false
        tasks.insertEvent(task.userTaskKey, null, "DISCOVERED", null, task.assignee, emptyList(), "RECONCILER", now)
        events.publishEvent(TaskChanged(task.userTaskKey, TaskState.OPEN, task.domain))
        return true
    }
}

package io.miragon.tasklist.taskmanager.api

import io.miragon.tasklist.contract.FormView
import io.miragon.tasklist.contract.HtmlFormView
import io.miragon.tasklist.contract.JsonFormsView
import io.miragon.tasklist.contract.TaskSubmission
import io.miragon.tasklist.contract.UserContext
import io.miragon.tasklist.taskmanager.applications.Applications
import io.miragon.tasklist.taskmanager.projection.TaskRepository
import io.miragon.tasklist.taskmanager.projection.UserTaskRow
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

/** An application found a task gone that the projection still shows as open. */
data class TaskReportedInactive(val task: UserTaskRow)

/**
 * The rules of the tasklist: who may do what with a task, and how forms and completions reach the owning application.
 * The application always gets a context built from the projection, never what the browser claims (#158).
 */
@Service
class TaskService(
    private val tasks: TaskRepository,
    private val commands: TaskCommands,
    private val applications: Applications,
    private val formRefs: FormRefs,
    private val events: ApplicationEventPublisher,
) {
    fun get(userTaskKey: Long): UserTaskRow = tasks.find(userTaskKey) ?: throw TaskNotFound(userTaskKey)

    /** Returns the task and whether Camunda confirmed the claim in time. */
    fun claim(userTaskKey: Long, user: UserContext): Pair<UserTaskRow, Boolean> {
        val task = open(userTaskKey)
        if (!task.isCandidate(user)) throw NotAllowed("Only members of ${task.candidateGroups.joinToString()} can claim this task.")
        if (task.assignee == user.id) return task to true
        if (task.assignee != null) throw ApiProblem(409, "ALREADY_ASSIGNED", "${task.assignee} already works on this task.")
        val confirmed = commands.assign(userTaskKey, user.id)
        return get(userTaskKey) to confirmed
    }

    fun unclaim(userTaskKey: Long, user: UserContext): Pair<UserTaskRow, Boolean> {
        val task = open(userTaskKey)
        if (task.assignee != user.id) throw NotAllowed("Only the assignee can hand the task back.")
        val confirmed = commands.unassign(userTaskKey)
        return get(userTaskKey) to confirmed
    }

    fun update(userTaskKey: Long, user: UserContext, update: TaskUpdate): Pair<UserTaskRow, Boolean> {
        val task = open(userTaskKey)
        if (!task.isCandidate(user)) throw NotAllowed("Only candidates of this task can change it.")
        update.priority?.let { if (it !in 0..100) throw ApiProblem(400, "BAD_REQUEST", "The priority must be between 0 and 100.") }
        val confirmed = commands.update(userTaskKey, update)
        return get(userTaskKey) to confirmed
    }

    /** Anybody may look at a form; it is editable only for the assignee of an open task. */
    fun form(userTaskKey: Long, user: UserContext): FormView {
        val task = get(userTaskKey)
        val view = applications.client(task.domain).form(task.toContext(user, formRefs.of(task)))
        val readOnly = view.readOnly || !task.isOpen || task.assignee != user.id
        return when (view) {
            is JsonFormsView -> view.copy(readOnly = readOnly, draftable = view.draftable && !readOnly)
            is HtmlFormView -> view.copy(readOnly = readOnly, draftable = view.draftable && !readOnly)
        }
    }

    fun draft(userTaskKey: Long, user: UserContext, data: Map<String, Any?>) {
        val task = workableBy(userTaskKey, user)
        forward(task) { applications.client(task.domain).draft(TaskSubmission(task.toContext(user, formRefs.of(task)), data)) }
    }

    /** Returns 204 or 202 as the application answered. */
    fun complete(userTaskKey: Long, user: UserContext, data: Map<String, Any?>): Int {
        val task = workableBy(userTaskKey, user)
        return forward(task) {
            applications.client(task.domain).complete(TaskSubmission(task.toContext(user, formRefs.of(task)), data))
        }
    }

    private fun open(userTaskKey: Long): UserTaskRow =
        get(userTaskKey).also { if (!it.isOpen) throw TaskNotActive("The task was ${it.state.name.lowercase()}.") }

    private fun workableBy(userTaskKey: Long, user: UserContext): UserTaskRow =
        open(userTaskKey).also { if (it.assignee != user.id) throw NotAllowed("Claim the task before you work on it.") }

    /** If the application finds the task gone, the projection missed it (e.g. a modification): have it corrected. */
    private fun <T> forward(task: UserTaskRow, call: () -> T): T =
        try {
            call()
        } catch (e: ApiProblem) {
            if (e.code == "TASK_NOT_ACTIVE") events.publishEvent(TaskReportedInactive(task))
            throw e
        }
}

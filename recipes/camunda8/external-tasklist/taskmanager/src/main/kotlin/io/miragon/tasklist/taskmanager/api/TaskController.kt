package io.miragon.tasklist.taskmanager.api

import io.miragon.tasklist.contract.FormView
import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.contract.UserContext
import io.miragon.tasklist.taskmanager.applications.Applications
import io.miragon.tasklist.taskmanager.projection.TaskQuery
import io.miragon.tasklist.taskmanager.projection.TaskRepository
import io.miragon.tasklist.taskmanager.projection.UserTaskRow
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val tasks: TaskRepository,
    private val service: TaskService,
    private val applications: Applications,
) {
    enum class StateFilter(val states: List<TaskState>?) {
        OPEN(listOf(TaskState.OPEN)),
        COMPLETED(listOf(TaskState.COMPLETED)),
        CANCELED(listOf(TaskState.CANCELED)),
        CLOSED(listOf(TaskState.COMPLETED, TaskState.CANCELED)),
        ALL(null),
    }

    @GetMapping
    fun list(
        user: UserContext?,
        @RequestParam(defaultValue = "OPEN") state: StateFilter,
        @RequestParam(required = false) domain: String?,
        @RequestParam(defaultValue = "ANY") assignment: TaskQuery.Assignment,
        @RequestParam(required = false) candidateGroup: String?,
        @RequestParam(required = false) processDefinitionId: String?,
        @RequestParam(required = false) processInstanceKey: Long?,
        @RequestParam(required = false) priorityMin: Int?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) dueBefore: OffsetDateTime?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "CREATED_AT") sort: TaskQuery.Sort,
        @RequestParam(required = false) descending: Boolean?,
        @RequestParam(defaultValue = "0") from: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ): TaskPage {
        if (assignment == TaskQuery.Assignment.MINE && user == null) {
            throw ApiProblem(401, "UNAUTHORIZED", "Choose a demo user to see your own tasks.")
        }
        val query = TaskQuery(
            states = state.states,
            domain = domain,
            assignment = assignment,
            userId = user?.id,
            candidateGroup = candidateGroup,
            processDefinitionId = processDefinitionId,
            processInstanceKey = processInstanceKey,
            priorityMin = priorityMin,
            dueBefore = dueBefore,
            q = q,
            sort = sort,
            descending = descending,
            from = from.coerceAtLeast(0),
            limit = limit.coerceIn(1, 200),
        )
        val (rows, total) = tasks.search(query)
        return TaskPage(rows.map { it.toDto(user) }, total, query.from, query.limit)
    }

    @GetMapping("/facets")
    fun facets(user: UserContext?, @RequestParam(defaultValue = "OPEN") state: StateFilter): Facets {
        val counts = tasks.facets(state.states, user?.id)
        return Facets(
            assignments = listOfNotNull(
                Facet(TaskQuery.Assignment.ANY.name, "All tasks", counts.assignments.any),
                user?.let { Facet(TaskQuery.Assignment.MINE.name, "Mine", counts.assignments.mine) },
                Facet(TaskQuery.Assignment.UNASSIGNED.name, "Unassigned", counts.assignments.unassigned),
            ),
            domains = counts.domains.map { (domain, count) -> Facet(domain, applications.label(domain), count) },
            candidateGroups = counts.candidateGroups.map { (group, count) -> Facet(group, group, count) },
            processes = counts.processes.map { (id, count) -> Facet(id, counts.processNames[id] ?: id, count) },
            states = counts.states.map { (value, count) -> Facet(value, value.lowercase().replaceFirstChar(Char::titlecase), count) },
        )
    }

    @GetMapping("/{userTaskKey}")
    fun get(@PathVariable userTaskKey: Long, user: UserContext?): TaskDto = service.get(userTaskKey).toDto(user)

    @GetMapping("/{userTaskKey}/history")
    fun history(@PathVariable userTaskKey: Long): List<HistoryEntry> {
        service.get(userTaskKey)
        return tasks.history(userTaskKey).map(HistoryEntry::from)
    }

    @GetMapping("/{userTaskKey}/form")
    fun form(@PathVariable userTaskKey: Long, user: UserContext): FormView = service.form(userTaskKey, user)

    @PostMapping("/{userTaskKey}/assignment")
    fun claim(@PathVariable userTaskKey: Long, user: UserContext): ResponseEntity<TaskDto> =
        service.claim(userTaskKey, user).toResponse(user)

    @DeleteMapping("/{userTaskKey}/assignment")
    fun unclaim(@PathVariable userTaskKey: Long, user: UserContext): ResponseEntity<TaskDto> =
        service.unclaim(userTaskKey, user).toResponse(user)

    @PatchMapping("/{userTaskKey}")
    fun update(@PathVariable userTaskKey: Long, user: UserContext, @RequestBody update: TaskUpdate): ResponseEntity<TaskDto> =
        service.update(userTaskKey, user, update).toResponse(user)

    @PostMapping("/{userTaskKey}/draft")
    fun draft(@PathVariable userTaskKey: Long, user: UserContext, @RequestBody body: SubmissionBody): ResponseEntity<Void> {
        service.draft(userTaskKey, user, body.data)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{userTaskKey}/completion")
    fun complete(@PathVariable userTaskKey: Long, user: UserContext, @RequestBody body: SubmissionBody): ResponseEntity<Void> =
        ResponseEntity.status(service.complete(userTaskKey, user, body.data)).build()

    private fun UserTaskRow.toDto(user: UserContext?) = TaskDto.from(this, applications.label(domain), user)

    /** 200 when Camunda confirmed the change, 202 when its listeners are still running. */
    private fun Pair<UserTaskRow, Boolean>.toResponse(user: UserContext) =
        ResponseEntity.status(if (second) HttpStatus.OK else HttpStatus.ACCEPTED).body(first.toDto(user))
}

package io.miragon.tasklist.taskmanager.projection

import io.miragon.tasklist.contract.TaskState
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.time.OffsetDateTime

@Repository
class TaskRepository(private val jdbc: JdbcClient) {

    /**
     * Applies a listener event. Returns false for a redelivered or outdated job and for a task the listener closed:
     * the `last_job_key` guard keeps the projection idempotent. A listener event may still correct a task the
     * reconciler closed, because the reconciler only judges by Camunda's eventually consistent search.
     */
    fun upsert(event: UserTaskEvent, details: TaskDetails?, now: OffsetDateTime): Boolean {
        val closed = event.type.state != TaskState.OPEN
        return jdbc.sql(
            """
            INSERT INTO user_task (user_task_key, element_instance_key, process_instance_key, process_definition_key,
                process_definition_id, process_definition_version, element_id, tenant_id, name, process_name, domain,
                business_id, subject, form_ref, state, assignee, candidate_groups, candidate_users, priority, due_date,
                follow_up_date, created_at, updated_at, closed_at, closed_by, last_job_key, verified_at)
            VALUES (:userTaskKey, :elementInstanceKey, :processInstanceKey, :processDefinitionKey,
                :processDefinitionId, :processDefinitionVersion, :elementId, :tenantId, :name, :processName, :domain,
                :businessId, :subject, :formRef, :state, :assignee, :candidateGroups, :candidateUsers, :priority, :dueDate,
                :followUpDate, :now, :now, :closedAt, :closedBy, :jobKey, :now)
            ON CONFLICT (user_task_key) DO UPDATE SET
                name = COALESCE(user_task.name, EXCLUDED.name),
                process_name = COALESCE(user_task.process_name, EXCLUDED.process_name),
                form_ref = COALESCE(user_task.form_ref, EXCLUDED.form_ref),
                business_id = COALESCE(EXCLUDED.business_id, user_task.business_id),
                subject = COALESCE(EXCLUDED.subject, user_task.subject),
                state = EXCLUDED.state,
                assignee = EXCLUDED.assignee,
                candidate_groups = EXCLUDED.candidate_groups,
                candidate_users = EXCLUDED.candidate_users,
                priority = EXCLUDED.priority,
                due_date = EXCLUDED.due_date,
                follow_up_date = EXCLUDED.follow_up_date,
                updated_at = EXCLUDED.updated_at,
                closed_at = EXCLUDED.closed_at,
                closed_by = EXCLUDED.closed_by,
                last_job_key = EXCLUDED.last_job_key,
                verified_at = EXCLUDED.verified_at
            WHERE user_task.last_job_key < EXCLUDED.last_job_key
                AND (user_task.state = 'OPEN' OR user_task.closed_by = 'RECONCILER')
            """,
        )
            .param("userTaskKey", event.userTaskKey)
            .param("elementInstanceKey", event.elementInstanceKey)
            .param("processInstanceKey", event.processInstanceKey)
            .param("processDefinitionKey", event.processDefinitionKey)
            .param("processDefinitionId", event.processDefinitionId)
            .param("processDefinitionVersion", event.processDefinitionVersion)
            .param("elementId", event.elementId)
            .param("tenantId", event.tenantId)
            .param("name", details?.name, Types.VARCHAR)
            .param("processName", details?.processName, Types.VARCHAR)
            .param("domain", event.domain)
            .param("businessId", event.businessId, Types.VARCHAR)
            .param("subject", event.subject, Types.VARCHAR)
            .param("formRef", details?.formRef, Types.VARCHAR)
            .param("state", event.type.state.name)
            .param("assignee", event.assignee, Types.VARCHAR)
            .param("candidateGroups", event.candidateGroups.toTypedArray())
            .param("candidateUsers", event.candidateUsers.toTypedArray())
            .param("priority", event.priority)
            .param("dueDate", event.dueDate, Types.TIMESTAMP_WITH_TIMEZONE)
            .param("followUpDate", event.followUpDate, Types.TIMESTAMP_WITH_TIMEZONE)
            .param("now", now)
            .param("closedAt", if (closed) now else null, Types.TIMESTAMP_WITH_TIMEZONE)
            .param("closedBy", if (closed) "LISTENER" else null, Types.VARCHAR)
            .param("jobKey", event.jobKey)
            .update() == 1
    }

    /** Inserts a task the listener never reported, for example one created before the listener was registered. */
    fun insertFromCamunda(task: CamundaTask, now: OffsetDateTime): Boolean =
        jdbc.sql(
            """
            INSERT INTO user_task (user_task_key, element_instance_key, process_instance_key, process_definition_key,
                process_definition_id, process_definition_version, element_id, tenant_id, name, process_name, domain,
                business_id, form_ref, state, assignee, candidate_groups, candidate_users, priority, due_date,
                follow_up_date, created_at, updated_at, last_job_key, verified_at)
            VALUES (:userTaskKey, :elementInstanceKey, :processInstanceKey, :processDefinitionKey,
                :processDefinitionId, :processDefinitionVersion, :elementId, :tenantId, :name, :processName, :domain,
                :businessId, :formRef, 'OPEN', :assignee, :candidateGroups, :candidateUsers, :priority, :dueDate,
                :followUpDate, :createdAt, :now, 0, :now)
            ON CONFLICT (user_task_key) DO NOTHING
            """,
        )
            .param("userTaskKey", task.userTaskKey)
            .param("elementInstanceKey", task.elementInstanceKey)
            .param("processInstanceKey", task.processInstanceKey)
            .param("processDefinitionKey", task.processDefinitionKey)
            .param("processDefinitionId", task.processDefinitionId)
            .param("processDefinitionVersion", task.processDefinitionVersion)
            .param("elementId", task.elementId)
            .param("tenantId", task.tenantId)
            .param("name", task.name, Types.VARCHAR)
            .param("processName", task.processName, Types.VARCHAR)
            .param("domain", task.domain)
            .param("businessId", task.businessId, Types.VARCHAR)
            .param("formRef", task.formRef, Types.VARCHAR)
            .param("assignee", task.assignee, Types.VARCHAR)
            .param("candidateGroups", task.candidateGroups.toTypedArray())
            .param("candidateUsers", task.candidateUsers.toTypedArray())
            .param("priority", task.priority)
            .param("dueDate", task.dueDate, Types.TIMESTAMP_WITH_TIMEZONE)
            .param("followUpDate", task.followUpDate, Types.TIMESTAMP_WITH_TIMEZONE)
            .param("createdAt", task.createdAt)
            .param("now", now)
            .update() == 1

    fun insertEvent(
        userTaskKey: Long,
        jobKey: Long?,
        eventType: String,
        action: String?,
        assignee: String?,
        changedAttributes: List<String>,
        source: String,
        now: OffsetDateTime,
    ) {
        jdbc.sql(
            """
            INSERT INTO user_task_event (job_key, user_task_key, event_type, action, assignee, changed_attributes, source, occurred_at)
            VALUES (:jobKey, :userTaskKey, :eventType, :action, :assignee, :changedAttributes, :source, :now)
            ON CONFLICT (job_key) DO NOTHING
            """,
        )
            .param("jobKey", jobKey, Types.BIGINT)
            .param("userTaskKey", userTaskKey)
            .param("eventType", eventType)
            .param("action", action, Types.VARCHAR)
            .param("assignee", assignee, Types.VARCHAR)
            .param("changedAttributes", changedAttributes.toTypedArray())
            .param("source", source)
            .param("now", now)
            .update()
    }

    /** Closes an open task from the reconciler, unless a listener event changed it since it was read. */
    fun closeByReconciler(userTaskKey: Long, state: TaskState, expectedJobKey: Long, now: OffsetDateTime): Boolean =
        jdbc.sql(
            """
            UPDATE user_task SET state = :state, closed_at = :now, closed_by = 'RECONCILER', updated_at = :now, verified_at = :now
            WHERE user_task_key = :userTaskKey AND state = 'OPEN' AND last_job_key = :expectedJobKey
            """,
        )
            .param("state", state.name)
            .param("now", now)
            .param("userTaskKey", userTaskKey)
            .param("expectedJobKey", expectedJobKey)
            .update() == 1

    fun markVerified(userTaskKey: Long, now: OffsetDateTime) {
        jdbc.sql("UPDATE user_task SET verified_at = :now WHERE user_task_key = :userTaskKey")
            .param("now", now)
            .param("userTaskKey", userTaskKey)
            .update()
    }

    /** Fills in what was not known when the task was created; never overwrites a known value. */
    fun fillDetails(userTaskKey: Long, details: TaskDetails) {
        jdbc.sql(
            """
            UPDATE user_task SET name = COALESCE(name, :name), process_name = COALESCE(process_name, :processName),
                form_ref = COALESCE(form_ref, :formRef)
            WHERE user_task_key = :userTaskKey
            """,
        )
            .param("name", details.name, Types.VARCHAR)
            .param("processName", details.processName, Types.VARCHAR)
            .param("formRef", details.formRef, Types.VARCHAR)
            .param("userTaskKey", userTaskKey)
            .update()
    }

    /** Fills in the details of all tasks of a definition that lack them; returns the changed tasks. */
    fun fillDetailsOfDefinition(processDefinitionKey: Long, details: Map<String, TaskDetails>): List<TaskChanged> =
        details.flatMap { (elementId, task) ->
            jdbc.sql(
                """
                UPDATE user_task SET name = COALESCE(name, :name), process_name = COALESCE(process_name, :processName),
                    form_ref = COALESCE(form_ref, :formRef)
                WHERE process_definition_key = :processDefinitionKey AND element_id = :elementId
                    AND (name IS NULL OR process_name IS NULL OR form_ref IS NULL)
                RETURNING user_task_key, state, domain
                """,
            )
                .param("name", task.name, Types.VARCHAR)
                .param("processName", task.processName, Types.VARCHAR)
                .param("formRef", task.formRef, Types.VARCHAR)
                .param("processDefinitionKey", processDefinitionKey)
                .param("elementId", elementId)
                .query { rs, _ -> TaskChanged(rs.getLong(1), TaskState.valueOf(rs.getString(2)), rs.getString(3)) }
                .list()
        }

    fun existingKeys(userTaskKeys: List<Long>): Set<Long> =
        if (userTaskKeys.isEmpty()) {
            emptySet()
        } else {
            jdbc.sql("SELECT user_task_key FROM user_task WHERE user_task_key = ANY(:keys)")
                .param("keys", userTaskKeys.toTypedArray())
                .query { rs, _ -> rs.getLong(1) }
                .set()
        }

    fun find(userTaskKey: Long): UserTaskRow? =
        jdbc.sql("SELECT * FROM user_task WHERE user_task_key = :userTaskKey")
            .param("userTaskKey", userTaskKey)
            .query { rs, _ -> rs.toRow() }
            .optional()
            .orElse(null)

    fun exists(userTaskKey: Long): Boolean =
        jdbc.sql("SELECT count(*) FROM user_task WHERE user_task_key = :userTaskKey")
            .param("userTaskKey", userTaskKey)
            .query(Long::class.java)
            .single() > 0

    fun findOpenToVerify(before: OffsetDateTime, limit: Int): List<UserTaskRow> =
        jdbc.sql(
            """
            SELECT * FROM user_task WHERE state = 'OPEN' AND verified_at < :before AND updated_at < :before
            ORDER BY verified_at LIMIT :limit
            """,
        )
            .param("before", before)
            .param("limit", limit)
            .query { rs, _ -> rs.toRow() }
            .list()

    fun search(query: TaskQuery): Pair<List<UserTaskRow>, Long> {
        val where = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()
        query.states?.let {
            where += "state = ANY(:states)"
            params["states"] = it.map(TaskState::name).toTypedArray()
        }
        query.domain?.let {
            where += "domain = :domain"
            params["domain"] = it
        }
        when (query.assignment) {
            TaskQuery.Assignment.MINE -> {
                where += "assignee = :assignee"
                params["assignee"] = query.userId
            }
            TaskQuery.Assignment.UNASSIGNED -> where += "assignee IS NULL"
            TaskQuery.Assignment.ANY -> Unit
        }
        query.candidateGroup?.let {
            where += ":candidateGroup = ANY(candidate_groups)"
            params["candidateGroup"] = it
        }
        query.processDefinitionId?.let {
            where += "process_definition_id = :processDefinitionId"
            params["processDefinitionId"] = it
        }
        query.priorityMin?.let {
            where += "priority >= :priorityMin"
            params["priorityMin"] = it
        }
        query.dueBefore?.let {
            where += "due_date < :dueBefore"
            params["dueBefore"] = it
        }
        query.q?.takeIf { it.isNotBlank() }?.let {
            where += "(name ILIKE :q ESCAPE '\\' OR subject ILIKE :q ESCAPE '\\' OR business_id ILIKE :q ESCAPE '\\' OR element_id ILIKE :q ESCAPE '\\')"
            params["q"] = "%" + it.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        val condition = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        val total = jdbc.sql("SELECT count(*) FROM user_task $condition")
            .params(params)
            .query(Long::class.java)
            .single()
        val rows = jdbc.sql("SELECT * FROM user_task $condition ORDER BY ${query.orderBy()} LIMIT :limit OFFSET :offset")
            .params(params)
            .param("limit", query.limit)
            .param("offset", query.from)
            .query { rs, _ -> rs.toRow() }
            .list()
        return rows to total
    }

    fun facets(states: List<TaskState>?): FacetCounts {
        val condition = if (states == null) "" else "WHERE state = ANY(:states)"
        fun count(select: String) = jdbc.sql(select.replace("#WHERE", condition))
            .apply { if (states != null) param("states", states.map(TaskState::name).toTypedArray()) }
            .query { rs, _ -> rs.getString(1) to rs.getLong(2) }
            .list()
        return FacetCounts(
            domains = count("SELECT domain, count(*) FROM user_task #WHERE GROUP BY domain ORDER BY domain"),
            candidateGroups = count(
                "SELECT g, count(*) FROM user_task CROSS JOIN LATERAL unnest(candidate_groups) AS g #WHERE GROUP BY g ORDER BY g",
            ),
            processes = count(
                "SELECT process_definition_id, count(*) FROM user_task #WHERE GROUP BY process_definition_id ORDER BY 1",
            ),
            states = jdbc.sql("SELECT state, count(*) FROM user_task GROUP BY state ORDER BY state")
                .query { rs, _ -> rs.getString(1) to rs.getLong(2) }
                .list(),
            processNames = jdbc.sql(
                "SELECT process_definition_id, max(process_name) FROM user_task GROUP BY process_definition_id",
            )
                .query { rs, _ -> rs.getString(1) to rs.getString(2) }
                .list()
                .toMap(),
        )
    }

    fun history(userTaskKey: Long): List<HistoryRow> =
        jdbc.sql("SELECT * FROM user_task_event WHERE user_task_key = :userTaskKey ORDER BY occurred_at, id")
            .param("userTaskKey", userTaskKey)
            .query { rs, _ ->
                HistoryRow(
                    eventType = rs.getString("event_type"),
                    action = rs.getString("action"),
                    assignee = rs.getString("assignee"),
                    changedAttributes = rs.strings("changed_attributes"),
                    source = rs.getString("source"),
                    occurredAt = rs.getObject("occurred_at", OffsetDateTime::class.java),
                )
            }
            .list()

    private fun ResultSet.toRow() = UserTaskRow(
        userTaskKey = getLong("user_task_key"),
        elementInstanceKey = getLong("element_instance_key"),
        processInstanceKey = getLong("process_instance_key"),
        processDefinitionKey = getLong("process_definition_key"),
        processDefinitionId = getString("process_definition_id"),
        processDefinitionVersion = getInt("process_definition_version"),
        elementId = getString("element_id"),
        tenantId = getString("tenant_id"),
        name = getString("name"),
        processName = getString("process_name"),
        domain = getString("domain"),
        businessId = getString("business_id"),
        subject = getString("subject"),
        formRef = getString("form_ref"),
        state = TaskState.valueOf(getString("state")),
        assignee = getString("assignee"),
        candidateGroups = strings("candidate_groups"),
        candidateUsers = strings("candidate_users"),
        priority = getInt("priority"),
        dueDate = getObject("due_date", OffsetDateTime::class.java),
        followUpDate = getObject("follow_up_date", OffsetDateTime::class.java),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        closedAt = getObject("closed_at", OffsetDateTime::class.java),
        closedBy = getString("closed_by"),
        lastJobKey = getLong("last_job_key"),
        verifiedAt = getObject("verified_at", OffsetDateTime::class.java),
    )

    @Suppress("UNCHECKED_CAST")
    private fun ResultSet.strings(column: String): List<String> =
        (getArray(column)?.array as? Array<String>)?.toList().orEmpty()
}

data class FacetCounts(
    val domains: List<Pair<String, Long>>,
    val candidateGroups: List<Pair<String, Long>>,
    val processes: List<Pair<String, Long>>,
    val states: List<Pair<String, Long>>,
    val processNames: Map<String, String?>,
)

/** Name, process name and form reference of a user task, taken from the BPMN model or from Camunda. */
data class TaskDetails(val name: String?, val processName: String?, val formRef: String?)

/** A user task as Camunda's search reports it, for tasks the listener never delivered. */
data class CamundaTask(
    val userTaskKey: Long,
    val elementInstanceKey: Long,
    val processInstanceKey: Long,
    val processDefinitionKey: Long,
    val processDefinitionId: String,
    val processDefinitionVersion: Int,
    val elementId: String,
    val tenantId: String,
    val name: String?,
    val processName: String?,
    val domain: String,
    val businessId: String?,
    val formRef: String?,
    val assignee: String?,
    val candidateGroups: List<String>,
    val candidateUsers: List<String>,
    val priority: Int,
    val dueDate: OffsetDateTime?,
    val followUpDate: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)

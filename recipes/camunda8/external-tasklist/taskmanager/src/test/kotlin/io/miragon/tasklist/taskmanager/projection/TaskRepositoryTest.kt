package io.miragon.tasklist.taskmanager.projection

import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.taskmanager.PostgresTestConfiguration
import io.miragon.tasklist.taskmanager.projection.UserTaskEvent.EventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest
import org.springframework.context.annotation.Import
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicLong

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TaskRepository::class, PostgresTestConfiguration::class)
class TaskRepositoryTest(@Autowired private val tasks: TaskRepository) {

    private val now = OffsetDateTime.now()

    @Test
    fun `applies listener events in job key order and ignores redeliveries`() {
        val created = event(EventType.CREATING, jobKey = 10)
        val assigned = event(EventType.ASSIGNING, jobKey = 20, userTaskKey = created.userTaskKey, assignee = "alice")

        assertThat(tasks.upsert(created, details, now)).isTrue()
        assertThat(tasks.upsert(assigned, details, now)).isTrue()
        assertThat(tasks.upsert(assigned, details, now)).isFalse()
        assertThat(tasks.upsert(created, details, now)).isFalse()

        val row = tasks.find(created.userTaskKey)!!
        assertThat(row.state).isEqualTo(TaskState.OPEN)
        assertThat(row.assignee).isEqualTo("alice")
        assertThat(row.lastJobKey).isEqualTo(20)
        assertThat(row.name).isEqualTo("Check order")
        assertThat(row.formRef).isEqualTo("check-order@1")
    }

    @Test
    fun `never reopens a closed task`() {
        val created = event(EventType.CREATING, jobKey = 10)
        tasks.upsert(created, details, now)
        tasks.upsert(event(EventType.CANCELING, jobKey = 20, userTaskKey = created.userTaskKey), details, now)

        assertThat(tasks.upsert(event(EventType.ASSIGNING, jobKey = 30, userTaskKey = created.userTaskKey), details, now)).isFalse()
        assertThat(tasks.find(created.userTaskKey)!!.state).isEqualTo(TaskState.CANCELED)
        assertThat(tasks.find(created.userTaskKey)!!.closedBy).isEqualTo("LISTENER")
    }

    @Test
    fun `lets the reconciler close a task only if no listener event came in between`() {
        val created = event(EventType.CREATING, jobKey = 10)
        tasks.upsert(created, details, now)

        assertThat(tasks.closeByReconciler(created.userTaskKey, TaskState.CANCELED, expectedJobKey = 9, now)).isFalse()
        assertThat(tasks.closeByReconciler(created.userTaskKey, TaskState.CANCELED, expectedJobKey = 10, now)).isTrue()
        assertThat(tasks.find(created.userTaskKey)!!.closedBy).isEqualTo("RECONCILER")
    }

    @Test
    fun `filters by domain, assignment, group, state and text`() {
        val order = event(EventType.CREATING, jobKey = 10, domain = "order", groups = listOf("sales"), subject = "Order ORD-1001 · Alice")
        val payment = event(EventType.CREATING, jobKey = 10, domain = "payment", groups = listOf("finance"), assignee = "bob")
        val done = event(EventType.CREATING, jobKey = 10, domain = "order", groups = listOf("sales"))
        listOf(order, payment, done).forEach { tasks.upsert(it, details, now) }
        tasks.upsert(event(EventType.COMPLETING, jobKey = 20, userTaskKey = done.userTaskKey), details, now)

        fun keys(query: TaskQuery) = tasks.search(query).first.map { it.userTaskKey }

        assertThat(keys(TaskQuery(domain = "order"))).containsExactly(order.userTaskKey)
        assertThat(keys(TaskQuery(assignment = TaskQuery.Assignment.MINE, userId = "bob"))).containsExactly(payment.userTaskKey)
        assertThat(keys(TaskQuery(assignment = TaskQuery.Assignment.UNASSIGNED))).containsExactly(order.userTaskKey)
        assertThat(keys(TaskQuery(candidateGroup = "finance"))).containsExactly(payment.userTaskKey)
        assertThat(keys(TaskQuery(q = "ord-1001"))).containsExactly(order.userTaskKey)
        assertThat(keys(TaskQuery(states = listOf(TaskState.COMPLETED)))).containsExactly(done.userTaskKey)
        assertThat(tasks.search(TaskQuery(states = null)).second).isEqualTo(3)

        val facets = tasks.facets(listOf(TaskState.OPEN))
        assertThat(facets.domains).containsExactly("order" to 1L, "payment" to 1L)
        assertThat(facets.candidateGroups).containsExactly("finance" to 1L, "sales" to 1L)
    }

    private val details = TaskDetails("Check order", "Order", "check-order@1")

    private fun event(
        type: EventType,
        jobKey: Long,
        userTaskKey: Long = nextKey.incrementAndGet(),
        assignee: String? = null,
        domain: String = "order",
        groups: List<String> = listOf("sales"),
        subject: String? = null,
    ) = UserTaskEvent(
        jobKey = jobKey,
        type = type,
        userTaskKey = userTaskKey,
        elementInstanceKey = 1,
        processInstanceKey = 2,
        processDefinitionKey = 3,
        processDefinitionId = "$domain-process",
        processDefinitionVersion = 1,
        elementId = "checkOrder",
        tenantId = "<default>",
        domain = domain,
        businessId = "ORD-$userTaskKey",
        subject = subject,
        assignee = assignee,
        candidateGroups = groups,
        candidateUsers = emptyList(),
        priority = 50,
        dueDate = null,
        followUpDate = null,
        action = null,
        changedAttributes = emptyList(),
    )

    private companion object {
        val nextKey = AtomicLong(1000)
    }
}

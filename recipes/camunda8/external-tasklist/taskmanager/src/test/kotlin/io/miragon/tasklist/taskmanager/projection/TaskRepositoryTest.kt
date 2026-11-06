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
    fun `lets a later listener event correct a task the reconciler closed`() {
        val created = event(EventType.CREATING, jobKey = 10)
        tasks.upsert(created, details, now)
        tasks.closeByReconciler(created.userTaskKey, TaskState.CANCELED, expectedJobKey = 10, now)

        assertThat(tasks.upsert(event(EventType.ASSIGNING, jobKey = 20, userTaskKey = created.userTaskKey, assignee = "bob"), details, now)).isTrue()

        val row = tasks.find(created.userTaskKey)!!
        assertThat(row.state).isEqualTo(TaskState.OPEN)
        assertThat(row.assignee).isEqualTo("bob")
        assertThat(row.closedBy).isNull()
    }

    @Test
    fun `searches text literally`() {
        val percent = event(EventType.CREATING, jobKey = 10, subject = "Discount 100% · Order ORD-7")
        val other = event(EventType.CREATING, jobKey = 10, subject = "Order ORD-8")
        listOf(percent, other).forEach { tasks.upsert(it, details, now) }

        assertThat(tasks.search(TaskQuery(q = "100%")).first.map { it.userTaskKey }).containsExactly(percent.userTaskKey)
        assertThat(tasks.search(TaskQuery(q = "%")).first.map { it.userTaskKey }).containsExactly(percent.userTaskKey)
        assertThat(tasks.search(TaskQuery(q = "ORD_8")).first).isEmpty()
    }

    @Test
    fun `fills in the details of a definition once the BPMN is known`() {
        val bare = event(EventType.CREATING, jobKey = 10)
        tasks.upsert(bare, null, now)

        val changed = tasks.fillDetailsOfDefinition(bare.processDefinitionKey, mapOf(bare.elementId to details))

        assertThat(changed.map { it.userTaskKey }).contains(bare.userTaskKey)
        assertThat(tasks.find(bare.userTaskKey)!!.formRef).isEqualTo("check-order@1")
        assertThat(tasks.existingKeys(listOf(bare.userTaskKey, -1))).containsExactly(bare.userTaskKey)
    }

    @Test
    fun `filters by domain, assignment, group, state and text`() {
        val order = event(EventType.CREATING, jobKey = 10, domain = "order", groups = listOf("sales"), subject = "Order ORD-1001 · Alice")
        val payment = event(EventType.CREATING, jobKey = 10, domain = "payment", groups = listOf("finance"), assignee = "bob", processInstanceKey = 7)
        val done = event(EventType.CREATING, jobKey = 10, domain = "order", groups = listOf("sales"))
        listOf(order, payment, done).forEach { tasks.upsert(it, details, now) }
        tasks.upsert(event(EventType.COMPLETING, jobKey = 20, userTaskKey = done.userTaskKey), details, now)

        fun keys(query: TaskQuery) = tasks.search(query).first.map { it.userTaskKey }

        assertThat(keys(TaskQuery(domain = "order"))).containsExactly(order.userTaskKey)
        assertThat(keys(TaskQuery(assignment = TaskQuery.Assignment.MINE, userId = "bob"))).containsExactly(payment.userTaskKey)
        assertThat(keys(TaskQuery(assignment = TaskQuery.Assignment.UNASSIGNED))).containsExactly(order.userTaskKey)
        assertThat(keys(TaskQuery(candidateGroup = "finance"))).containsExactly(payment.userTaskKey)
        assertThat(keys(TaskQuery(q = "ord-1001"))).containsExactly(order.userTaskKey)
        assertThat(keys(TaskQuery(processInstanceKey = 7))).containsExactly(payment.userTaskKey)
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
        processInstanceKey: Long = 2,
    ) = UserTaskEvent(
        jobKey = jobKey,
        type = type,
        userTaskKey = userTaskKey,
        elementInstanceKey = 1,
        processInstanceKey = processInstanceKey,
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

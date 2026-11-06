package io.miragon.tasklist.payment

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientException
import io.camunda.client.api.search.response.UserTask
import io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance
import io.camunda.process.test.api.CamundaAssert.assertThatUserTask
import io.camunda.process.test.api.CamundaProcessTestContext
import io.camunda.process.test.api.CamundaSpringProcessTest
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey
import io.camunda.process.test.api.assertions.UserTaskSelectors.byProcessInstanceKey
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.contract.TaskSubmission
import io.miragon.tasklist.contract.UserContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.client.RestTestClient
import java.time.Duration

/**
 * Runs the application against Camunda (Camunda Process Test, one container for the class) and PostgreSQL.
 * A fake order application receives the payment results. The task manager is not part of this test:
 * the test calls the provider API itself, with a task context built from the user task in Camunda.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["payment.callback.allowed-hosts=localhost", "payment.callback.retry-backoff=1s"],
)
@CamundaSpringProcessTest
@Import(PostgresContainer::class, FakeOrderApplication::class)
class PaymentProcessTest {

    @Autowired
    private lateinit var camunda: CamundaClient

    @Autowired
    private lateinit var processTest: CamundaProcessTestContext

    @Autowired
    private lateinit var orderApplication: FakeOrderApplication

    @Autowired
    private lateinit var payments: PaymentRepository

    @Autowired
    private lateinit var jdbc: JdbcClient

    @MockitoSpyBean
    private lateinit var process: PaymentProcess

    @LocalServerPort
    private var port = 0

    /** The JDK client: the Apache client on the classpath would repeat a request that got 503 on its own. */
    private val http by lazy {
        RestTestClient.bindToServer(JdkClientHttpRequestFactory()).baseUrl("http://localhost:$port").build()
    }

    private val received = mapOf("paymentReceived" to true, "amountReceived" to 129.90)

    @BeforeEach
    fun forgetEarlierPayments() {
        jdbc.sql("DELETE FROM payments").update()
        orderApplication.reset()
    }

    @Test
    fun `requests a payment once per order`() {
        val paymentId = requestPayment("ORD-1001")
            .expectStatus().isCreated()
            .expectHeader().valueMatches(HttpHeaders.LOCATION, "/api/v1/payments/PAY-\\d+")
            .returnResult(Map::class.java).responseBody!!["paymentId"] as String

        requestPayment("ORD-1001")
            .expectStatus().isOk()
            .expectBody().jsonPath("$.paymentId").isEqualTo(paymentId)
        requestPayment("ORD-1001", amount = "99.00")
            .expectStatus().isEqualTo(409)
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.paymentId").isEqualTo(paymentId)

        http.get().uri("/api/v1/payments/{id}", paymentId).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("ORD-1001")
            .jsonPath("$.amount").isEqualTo(129.90)
            .jsonPath("$.status").isEqualTo("OPEN")
    }

    @Test
    fun `refuses what it cannot serve and stores nothing`() {
        requestPayment("ORD-1002", callbackUrl = "http://payment-db:5432/")
            .expectStatus().isBadRequest()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.errors[0].field").isEqualTo("callbackUrl")
        http.post().uri("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).body("""{"orderId": "ORD-1002"}""").exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
        http.get().uri("/api/v1/payments/PAY-1").exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)

        doThrow(ClientException("Camunda is not available")).`when`(process).start(anyPayment())
        requestPayment("ORD-1002")
            .expectStatus().isEqualTo(503)
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)

        assertThat(payments.findByOrderId("ORD-1002")).isNull()
    }

    @Test
    fun `opens the check payment task for the finance team`() {
        val payment = createPayment()

        assertThatProcessInstance(byKey(payment.processInstanceKey!!))
            .isActive()
            .hasActiveElements("checkPayment")
            .hasVariable("subject", "Payment ${payment.id} · Order ORD-1001 · 129.90 EUR")
        assertThatUserTask(byProcessInstanceKey(payment.processInstanceKey)).isCreated().hasCandidateGroup("finance")
        val task = checkPaymentTask(payment)
        assertThat(task.customHeaders).containsEntry("domain", "payment")
        assertThat(task.externalFormReference).isEqualTo("check-payment@1")
        assertThat(task.businessId).isEqualTo(payment.id)

        openForm(task, assignee = "anna")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.type").isEqualTo("json-forms")
            .jsonPath("$.formRef").isEqualTo("check-payment@1")
            .jsonPath("$.readOnly").isEqualTo(false)
            .jsonPath("$.data.invoiceAmount").isEqualTo("129.90 EUR")
            .jsonPath("$.schema.required[0]").isEqualTo("paymentReceived")
        openForm(task, assignee = null)
            .expectStatus().isOk()
            .expectBody().jsonPath("$.readOnly").isEqualTo(true)
    }

    @Test
    fun `reports a received payment to the order application`() {
        val payment = createPayment()

        completeTask(checkPaymentTask(payment), received).expectStatus().isNoContent()

        assertThatProcessInstance(byKey(payment.processInstanceKey!!))
            .isCompleted()
            .hasCompletedElements("checkPayment", "reportPaymentResult", "paymentChecked")
            .hasVariable("paymentStatus", "PAID")
        assertThat(orderApplication.results).containsExactly(
            mapOf("orderId" to "ORD-1001", "paymentId" to payment.id, "status" to "PAID", "reason" to null),
        )
        val paid = payments.find(payment.id)!!
        assertThat(paid.status).isEqualTo(PaymentStatus.PAID)
        assertThat(paid.amountReceived).isEqualByComparingTo("129.90")
        assertThat(paid.reportedAt).isNotNull()
    }

    @Test
    fun `refuses a rejection without a reason and keeps the task open`() {
        val payment = createPayment()
        val task = checkPaymentTask(payment)

        completeTask(task, mapOf("paymentReceived" to false))
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.errors[0].field").isEqualTo("rejectionReason")
        completeTask(task, mapOf("paymentReceived" to true, "amountReceived" to 120))
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].message").isEqualTo("The received amount must match the invoice amount of 129.90 EUR")

        assertThatProcessInstance(byKey(payment.processInstanceKey!!)).isActive().hasActiveElements("checkPayment")
        assertThat(payments.find(payment.id)!!.status).isEqualTo(PaymentStatus.OPEN)
    }

    @Test
    fun `reminds the customer and keeps the task open`() {
        val payment = createPayment()
        checkPaymentTask(payment)

        processTest.increaseTime(Duration.ofDays(3))

        assertThatProcessInstance(byKey(payment.processInstanceKey!!))
            .isActive()
            .hasCompletedElements("sendPaymentReminder", "reminderSent")
            .hasActiveElements("checkPayment")
        assertThat(payments.find(payment.id)!!.remindersSent).isEqualTo(1)
    }

    @Test
    fun `rejects an overdue payment and reports it`() {
        val payment = createPayment()
        checkPaymentTask(payment)

        processTest.increaseTime(Duration.ofDays(14))

        assertThatProcessInstance(byKey(payment.processInstanceKey!!))
            .isCompleted()
            .hasTerminatedElements("checkPayment")
            .hasCompletedElements("markPaymentOverdue", "reportOverdueResult", "paymentOverdue")
        assertThatUserTask(byProcessInstanceKey(payment.processInstanceKey)).isCanceled()
        assertThat(orderApplication.results).containsExactly(
            mapOf("orderId" to "ORD-1001", "paymentId" to payment.id, "status" to "REJECTED", "reason" to "Payment overdue"),
        )
        assertThat(payments.find(payment.id)!!.status).isEqualTo(PaymentStatus.REJECTED)
    }

    @Test
    fun `retries a result the order application cannot take yet`() {
        orderApplication.answerNext(HttpStatus.SERVICE_UNAVAILABLE)
        val payment = createPayment()

        completeTask(checkPaymentTask(payment), received).expectStatus().isNoContent()

        assertThatProcessInstance(byKey(payment.processInstanceKey!!)).isCompleted().hasNoActiveIncidents()
        assertThat(orderApplication.results).hasSize(2)
    }

    @Test
    fun `raises an incident when the order application refuses the result`() {
        orderApplication.answerNext(HttpStatus.CONFLICT)
        val payment = createPayment()

        completeTask(checkPaymentTask(payment), received).expectStatus().isNoContent()

        assertThatProcessInstance(byKey(payment.processInstanceKey!!)).isActive().hasActiveIncidents()
        val incident = camunda.newIncidentSearchRequest()
            .filter { it.processInstanceKey(payment.processInstanceKey) }
            .send().join().items().single()
        assertThat(incident.errorMessage).startsWith("The order application answered 409")
        assertThat(orderApplication.results).hasSize(1)
    }

    private fun requestPayment(orderId: String, amount: String = "129.90", callbackUrl: String = callbackUrl(orderId)) =
        http.post().uri("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {"orderId": "$orderId", "amount": $amount, "currency": "EUR",
                 "customer": {"name": "Alice Smith", "email": "alice@example.com"}, "callbackUrl": "$callbackUrl"}
                """,
            )
            .exchange()

    private fun callbackUrl(orderId: String) = "http://localhost:$port/fake-order-app/api/v1/orders/$orderId/payment-result"

    /** Mockito's matcher returns null, which a Kotlin parameter refuses; the matcher still counts, not the stand-in. */
    private fun anyPayment(): Payment = any(Payment::class.java) ?: aPayment()

    private fun createPayment(orderId: String = "ORD-1001"): Payment {
        requestPayment(orderId).expectStatus().isCreated()
        return payments.findByOrderId(orderId)!!
    }

    private fun checkPaymentTask(payment: Payment): UserTask {
        assertThatUserTask(byProcessInstanceKey(payment.processInstanceKey!!)).isCreated()
        return camunda.newUserTaskSearchRequest()
            .filter { it.processInstanceKey(payment.processInstanceKey).elementId("checkPayment") }
            .send().join().items().single()
    }

    private fun openForm(task: UserTask, assignee: String?) =
        http.post().uri("/tasklist/v1/tasks/{key}/form", task.userTaskKey)
            .header(HttpHeaders.AUTHORIZATION, "Bearer payment-demo-token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(taskContext(task, assignee))
            .exchange()

    private fun completeTask(task: UserTask, data: Map<String, Any?>) =
        http.post().uri("/tasklist/v1/tasks/{key}/completion", task.userTaskKey)
            .header(HttpHeaders.AUTHORIZATION, "Bearer payment-demo-token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(TaskSubmission(taskContext(task, assignee = "anna"), data))
            .exchange()

    /** What the task manager would send: the task as Camunda knows it, claimed by Anna from finance. */
    private fun taskContext(task: UserTask, assignee: String?) = TaskContext(
        userTaskKey = task.userTaskKey.toString(),
        processInstanceKey = task.processInstanceKey.toString(),
        processDefinitionKey = task.processDefinitionKey.toString(),
        processDefinitionId = task.bpmnProcessId,
        processDefinitionVersion = task.processDefinitionVersion,
        elementId = task.elementId,
        formRef = task.externalFormReference,
        businessId = task.businessId,
        state = TaskState.OPEN,
        assignee = assignee,
        candidateGroups = task.candidateGroups,
        tenantId = task.tenantId,
        user = UserContext("anna", listOf("finance")),
    )
}

package io.miragon.tasklist.order

import io.camunda.client.CamundaClient
import io.camunda.client.api.search.response.UserTask
import io.camunda.process.test.api.CamundaAssert.assertThat
import io.camunda.process.test.api.CamundaSpringProcessTest
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey
import io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId
import io.miragon.tasklist.contract.UserContext
import io.miragon.tasklist.fakepayment.FakePaymentApp
import io.miragon.tasklist.order.AppClient.Companion.contextOf
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.ServerSocket
import java.time.Duration
import java.time.LocalDate

/**
 * Runs the order process on a Camunda engine in a container and drives it the way the task manager and the payment
 * application do: through the provider API and the orders API. The fake payment application is served by the
 * application under test itself.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = [
        "camunda.process-test.assertion.timeout=PT30S",
        "camunda.process-test.camunda-env-vars[JAVA_TOOL_OPTIONS]=-Xms256m -Xmx1g",
    ],
)
@CamundaSpringProcessTest
@Import(PostgresContainer::class, FakePaymentApp::class)
class OrderProcessTest(
    @Autowired private val camunda: CamundaClient,
    @Autowired private val payments: FakePaymentApp,
    @Autowired private val mapper: JsonMapper,
) {
    private val app = AppClient(BASE_URL, mapper)
    private val sales = UserContext("alice", listOf("sales"))
    private val warehouse = UserContext("walter", listOf("warehouse"))

    @BeforeEach
    fun resetPaymentApp() = payments.reset()

    @Test
    fun `places an order through the start form and lets sales check it`() {
        assertThat(app.provider("/tasklist/v1/start-forms").json.values().single())
            .isEqualTo(json("""{"id": "new-order", "formRef": "start-order@1", "label": "New order", "description": "Place an order for a MiraVelo customer"}"""))
        val startForm = app.provider("/tasklist/v1/start-forms/new-order/form", mapOf("user" to sales)).json
        assertThat(startForm["type"].asString()).isEqualTo("html")
        assertThat(startForm["data"]["items"].values().map { it["id"].asString() }).contains("city-helmet", "folding-lock")

        val (orderId, processInstanceKey) = placeOrder()

        assertThat(orderId).matches("ORD-\\d{4,}")
        assertThat(order(orderId)["status"].asString()).isEqualTo("CHECKING")
        assertThat(byKey(processInstanceKey)).isActive()
            .hasVariable("orderId", orderId)
            .hasVariable("subject", "Order $orderId · Alice Smith · 129.90 EUR")
        val task = openTask(processInstanceKey, "checkOrder")
        assertThat(task.businessId).isEqualTo(orderId)
        assertThat(task.candidateGroups).containsExactly("sales")
        assertThat(task.customHeaders).containsEntry("domain", "order")
        assertThat(task.externalFormReference).isEqualTo("check-order@1")

        val form = app.provider("/tasklist/v1/tasks/${task.userTaskKey}/form", contextOf(task, sales)).json
        assertThat(form["type"].asString()).isEqualTo("html")
        assertThat(form["readOnly"].asBoolean()).isFalse()
        assertThat(form["data"]["order"]).isEqualTo(
            json(
                """
                {"id": "$orderId", "status": "CHECKING", "total": 129.90, "currency": "EUR", "checkComment": null,
                 "customer": {"name": "Alice Smith", "email": "alice@example.com", "address": "Hauptstraße 1\n80331 München"},
                 "items": [{"name": "City helmet", "quantity": 1, "price": 79.90}, {"name": "Folding lock", "quantity": 1, "price": 50.00}]}
                """,
            ),
        )
        val unassigned = contextOf(task, UserContext("bob", listOf("sales")), assignee = null)
        assertThat(app.provider("/tasklist/v1/tasks/${task.userTaskKey}/form", unassigned).json["readOnly"].asBoolean()).isTrue()
    }

    @Test
    fun `explains which fields of a new order are wrong`() {
        val answer = app.provider(
            "/tasklist/v1/start-forms/new-order/submission",
            mapOf("user" to sales, "data" to mapOf("customer" to mapOf("name" to "", "email" to "alice", "address" to "x"), "items" to emptyList<Any>())),
        )

        assertThat(answer.status).isEqualTo(422)
        assertThat(answer.json["code"].asString()).isEqualTo("VALIDATION_FAILED")
        assertThat(answer.json["errors"].values().map { it["field"].asString() }).containsExactly("customer.name", "customer.email", "items")
    }

    @Test
    fun `ships an approved order once it is paid`() {
        payments.failNext(1)
        val (orderId, processInstanceKey) = placeOrder()

        assertThat(complete(openTask(processInstanceKey, "checkOrder"), sales, mapOf("approved" to true)).status).isEqualTo(204)

        // The payment application answers 503 first, so the worker retries after a backoff.
        await().atMost(Duration.ofSeconds(30)).until { payments.requestsFor(orderId).size == 2 }
        val (first, second) = payments.requestsFor(orderId)
        assertThat(Duration.between(first.at, second.at)).isGreaterThanOrEqualTo(Duration.ofMillis(1900))
        assertThat(second.body).containsEntry("currency", "EUR")
            .containsEntry("customer", mapOf("name" to "Alice Smith", "email" to "alice@example.com"))
            .containsEntry("callbackUrl", "$BASE_URL/api/v1/orders/$orderId/payment-result")
        assertThat((second.body["amount"] as Number).toDouble()).isEqualTo(129.9)
        assertThat(byKey(processInstanceKey)).isWaitingForMessage("PaymentReceived", orderId)
        assertThat(order(orderId)["payment"]["paymentId"].asString()).isEqualTo("PAY-$orderId")

        val paid = mapOf("paymentId" to "PAY-$orderId", "status" to "PAID")
        val callback = "/api/v1/orders/$orderId/payment-result"
        assertThat(app.post(callback, paid).status).isEqualTo(204)
        assertThat(app.post(callback, paid).status).isEqualTo(204)
        assertThat(app.post(callback, paid + ("status" to "REJECTED")).status).isEqualTo(409)
        assertThat(order(orderId)["status"].asString()).isEqualTo("PREPARING")

        val task = openTask(processInstanceKey, "prepareDelivery")
        assertThat(task.candidateGroups).containsExactly("warehouse")
        assertThat(task.customHeaders).containsEntry("domain", "order")
        assertThat(task.externalFormReference).isEqualTo("prepare-delivery@1")
        val form = app.provider("/tasklist/v1/tasks/${task.userTaskKey}/form", contextOf(task, warehouse)).json
        assertThat(form["type"].asString()).isEqualTo("json-forms")
        assertThat(form["draftable"].asBoolean()).isTrue()
        assertThat(form["data"]["items"].values().map { it["ready"].asBoolean() }).containsExactly(false, false)

        val shippingDate = LocalDate.now().plusDays(1).toString()
        val halfPacked = mapOf(
            "items" to listOf(mapOf("itemId" to "city-helmet", "ready" to true), mapOf("itemId" to "folding-lock", "ready" to false)),
            "shippingDate" to shippingDate,
            "deliveryNote" to "Leave it with the neighbours",
        )
        val draft = app.provider("/tasklist/v1/tasks/${task.userTaskKey}/draft", mapOf("context" to contextOf(task, warehouse), "data" to halfPacked))
        assertThat(draft.status).isEqualTo(204)
        val drafted = app.provider("/tasklist/v1/tasks/${task.userTaskKey}/form", contextOf(task, warehouse)).json["data"]
        assertThat(drafted["items"].values().map { it["ready"].asBoolean() }).containsExactly(true, false)
        assertThat(drafted["shippingDate"].asString()).isEqualTo(shippingDate)

        val incomplete = complete(task, warehouse, halfPacked)
        assertThat(incomplete.status).isEqualTo(422)
        assertThat(incomplete.json["errors"].values().map { it["field"].asString() }).containsExactly("items")

        val packed = halfPacked + ("items" to listOf(mapOf("itemId" to "city-helmet", "ready" to true), mapOf("itemId" to "folding-lock", "ready" to true)))
        assertThat(complete(task, warehouse, packed).status).isEqualTo(204)

        assertThat(byKey(processInstanceKey)).isCompleted().hasCompletedElements("orderShipped")
        assertThat(order(orderId)["status"].asString()).isEqualTo("SHIPPED")
        assertThat(order(orderId)["shippingDate"].asString()).isEqualTo(shippingDate)
    }

    @Test
    fun `declines an order only with a reason`() {
        val (orderId, processInstanceKey) = placeOrder()
        val task = openTask(processInstanceKey, "checkOrder")

        val withoutReason = complete(task, sales, mapOf("approved" to false))
        assertThat(withoutReason.status).isEqualTo(422)
        assertThat(withoutReason.json["errors"].values().single()["field"].asString()).isEqualTo("comment")
        assertThat(complete(task, sales, mapOf("approved" to false, "comment" to "Out of stock until spring")).status).isEqualTo(204)

        assertThat(byKey(processInstanceKey)).isCompleted().hasCompletedElements("orderDeclined")
        assertThat(order(orderId)["status"].asString()).isEqualTo("DECLINED")
        assertThat(order(orderId)["checkComment"].asString()).isEqualTo("Out of stock until spring")
        assertThat(payments.requests).isEmpty()
    }

    @Test
    fun `cancels the order and its open task when the customer cancels while sales checks it`() {
        val (orderId, processInstanceKey) = placeOrder()
        val task = openTask(processInstanceKey, "checkOrder")

        assertThat(app.post("/api/v1/orders/$orderId/cancellation", mapOf("reason" to "Found a cheaper helmet")).status).isEqualTo(204)

        assertThat(byElementId("checkOrder", processInstanceKey)).isCanceled()
        assertThat(byKey(processInstanceKey)).isCompleted().hasCompletedElements("orderCanceledEnd")
        assertThat(order(orderId)["status"].asString()).isEqualTo("CANCELED")
        assertThat(order(orderId)["cancelReason"].asString()).isEqualTo("Found a cheaper helmet")

        val lateCompletion = complete(task, sales, mapOf("approved" to true))
        assertThat(lateCompletion.status).isEqualTo(409)
        assertThat(lateCompletion.json["code"].asString()).isEqualTo("TASK_NOT_ACTIVE")
        assertThat(app.post("/api/v1/orders/$orderId/cancellation", mapOf("reason" to "Still canceled")).status).isEqualTo(204)
    }

    @Test
    fun `keeps an approved order and cancels it when the payment is rejected`() {
        val (orderId, processInstanceKey) = placeOrder()
        assertThat(complete(openTask(processInstanceKey, "checkOrder"), sales, mapOf("approved" to true)).status).isEqualTo(204)

        val cancellation = app.post("/api/v1/orders/$orderId/cancellation", mapOf("reason" to "Too late"))
        assertThat(cancellation.status).isEqualTo(409)
        assertThat(cancellation.json["detail"].asString()).isEqualTo("The order can no longer be canceled.")

        assertThat(byKey(processInstanceKey)).isWaitingForMessage("PaymentRejected", orderId)
        val rejected = mapOf("paymentId" to "PAY-$orderId", "status" to "REJECTED", "reason" to "Card expired")
        assertThat(app.post("/api/v1/orders/$orderId/payment-result", rejected).status).isEqualTo(204)

        assertThat(byKey(processInstanceKey)).isCompleted().hasCompletedElements("orderNotPaid")
        val order = order(orderId)
        assertThat(order["status"].asString()).isEqualTo("NOT_PAID")
        assertThat(order["payment"]).isEqualTo(json("""{"paymentId": "PAY-$orderId", "status": "REJECTED", "reason": "Card expired"}"""))
    }

    @Test
    fun `asks the payment application to report again while the order process does not wait for the result`() {
        val (orderId, _) = placeOrder()

        val early = app.post("/api/v1/orders/$orderId/payment-result", mapOf("paymentId" to "PAY-$orderId", "status" to "PAID"))

        assertThat(early.status).isEqualTo(503)
        assertThat(early.headers.getFirst("Retry-After")).isEqualTo("2")
        assertThat(early.json["code"].asString()).isEqualTo("PROCESS_NOT_WAITING")
        assertThat(order(orderId)["payment"].isNull).isTrue()
    }

    @Test
    fun `raises an incident when the payment application refuses the request`() {
        payments.refuseWith(HttpStatus.CONFLICT)
        val (orderId, processInstanceKey) = placeOrder()

        assertThat(complete(openTask(processInstanceKey, "checkOrder"), sales, mapOf("approved" to true)).status).isEqualTo(204)

        assertThat(byKey(processInstanceKey)).hasActiveIncidents()
        val incident = camunda.newIncidentSearchRequest().filter { it.processInstanceKey(processInstanceKey) }.send().join().items().single()
        // The incident comes at once, without retries, and shows the plain reason.
        assertThat(incident.errorMessage).isEqualTo("The payment application refused the payment request with 409: The amount differs from the first request.")
        assertThat(payments.requestsFor(orderId)).hasSize(1)
    }

    private fun placeOrder(): Pair<String, Long> {
        val answer = app.provider("/tasklist/v1/start-forms/new-order/submission", mapOf("user" to sales, "data" to ALICE_ORDER))
        assertThat(answer.status).isEqualTo(201)
        return answer.json["businessId"].asString() to answer.json["processInstanceKey"].asString().toLong()
    }

    private fun openTask(processInstanceKey: Long, elementId: String): UserTask {
        assertThat(byElementId(elementId, processInstanceKey)).isCreated()
        return camunda.newUserTaskSearchRequest()
            .filter { it.processInstanceKey(processInstanceKey).elementId(elementId) }
            .send()
            .join()
            .items()
            .single()
    }

    private fun complete(task: UserTask, user: UserContext, data: Map<String, Any?>) =
        app.provider("/tasklist/v1/tasks/${task.userTaskKey}/completion", mapOf("context" to contextOf(task, user), "data" to data))

    private fun order(orderId: String): JsonNode = app.get("/api/v1/orders/$orderId").json

    private fun json(text: String): JsonNode = mapper.readTree(text)

    companion object {
        private val PORT = ServerSocket(0).use { it.localPort }
        private val BASE_URL = "http://localhost:$PORT"

        private val ALICE_ORDER = mapOf(
            "customer" to mapOf("name" to "Alice Smith", "email" to "alice@example.com", "address" to "Hauptstraße 1\n80331 München"),
            "items" to listOf(mapOf("itemId" to "city-helmet", "quantity" to 1), mapOf("itemId" to "folding-lock", "quantity" to 1)),
        )

        /**
         * The fake payment application runs inside the application under test, and the payment request
         * and its callback URL must name that server. So the server listens on a port chosen up front.
         */
        @JvmStatic
        @DynamicPropertySource
        fun serverPort(registry: DynamicPropertyRegistry) {
            registry.add("server.port") { PORT }
            registry.add("order.payments.base-url") { BASE_URL }
            registry.add("order.callback-base-url") { BASE_URL }
        }
    }
}

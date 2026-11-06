package io.miragon.tasklist.taskmanager

import io.camunda.client.CamundaClient
import io.camunda.process.test.api.CamundaAssert
import io.camunda.process.test.api.CamundaSpringProcessTest
import io.miragon.tasklist.taskmanager.projection.GlobalListenerRegistration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.ServerSocket
import java.time.Duration

/**
 * The whole chain against a real Camunda 8.10: the global listener feeds the projection, the task manager
 * claims and updates through Camunda and forwards forms and completions to the owning application
 * (a provider inside this test).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@CamundaSpringProcessTest
@Import(PostgresTestConfiguration::class, ReviewProvider::class)
class TaskmanagerIntegrationTest(
    @Autowired private val camunda: CamundaClient,
    @Autowired private val mapper: JsonMapper,
    @Autowired private val registration: GlobalListenerRegistration,
    @Autowired private val jdbc: JdbcClient,
) {
    private val api = RestClient.create("http://localhost:$port")

    /**
     * Camunda Process Test purges the cluster before every test, the global listener included, and starts the keys
     * over. So each test starts with an empty projection and registers the listener again.
     */
    @BeforeEach
    fun resetAndRegisterListener() {
        jdbc.sql("TRUNCATE user_task_event, user_task").update()
        registration.registerNow()
        camunda.newDeployResourceCommand().addResourceFromClasspath("bpmn/review.bpmn").send().join()
        assertThat(call(HttpMethod.GET, "/actuator/health", user = null).json()["status"].asString()).isEqualTo("UP")
    }

    @Test
    fun `projects a new task with its name, form, domain and business data`() {
        val key = startReview("DOC-1")

        val task = call(HttpMethod.GET, "/api/tasks/$key").json()
        assertThat(task["name"].asString()).isEqualTo("Review document")
        assertThat(task["processName"].asString()).isEqualTo("Review")
        assertThat(task["formRef"].asString()).isEqualTo("review@1")
        assertThat(task["domain"].asString()).isEqualTo("review")
        assertThat(task["domainLabel"].asString()).isEqualTo("Reviews")
        assertThat(task["businessId"].asString()).isEqualTo("DOC-1")
        assertThat(task["subject"].asString()).isEqualTo("Document DOC-1")
        assertThat(task["candidateGroups"].strings()).containsExactly("sales")
        assertThat(task["actions"].strings()).containsExactlyInAnyOrder("CLAIM", "UPDATE")

        val list = call(HttpMethod.GET, "/api/tasks?domain=review&candidateGroup=sales&q=DOC-1").json()
        assertThat(list["items"].nodes().map { it["userTaskKey"].asString() }).contains(key)
    }

    @Test
    fun `claims, updates and completes a task through its application`() {
        val key = startReview("DOC-2")

        assertThat(call(HttpMethod.POST, "/api/tasks/$key/assignment", user = "bob").status).isEqualTo(403)
        val claimed = call(HttpMethod.POST, "/api/tasks/$key/assignment")
        assertThat(claimed.status).isEqualTo(200)
        assertThat(claimed.json()["assignee"].asString()).isEqualTo("alice")

        val updated = call(HttpMethod.PATCH, "/api/tasks/$key", body = mapOf("priority" to 80))
        assertThat(updated.json()["priority"].asInt()).isEqualTo(80)

        assertThat(call(HttpMethod.GET, "/api/tasks/$key/form").json()["readOnly"].asBoolean()).isFalse()
        assertThat(call(HttpMethod.GET, "/api/tasks/$key/form", user = "demo").json()["readOnly"].asBoolean()).isTrue()
        assertThat(call(HttpMethod.POST, "/api/tasks/$key/completion", user = "demo", body = mapOf("data" to mapOf("approved" to true))).status)
            .isEqualTo(403)

        val invalid = call(HttpMethod.POST, "/api/tasks/$key/completion", body = mapOf("data" to emptyMap<String, Any>()))
        assertThat(invalid.status).isEqualTo(422)
        assertThat(invalid.json()["errors"][0]["field"].asString()).isEqualTo("approved")

        val completed = call(HttpMethod.POST, "/api/tasks/$key/completion", body = mapOf("data" to mapOf("approved" to true, "documentId" to "forged")))
        assertThat(completed.status).isEqualTo(204)
        // The completion returned only after the listener updated the projection: no waiting needed.
        val task = call(HttpMethod.GET, "/api/tasks/$key").json()
        assertThat(task["state"].asString()).isEqualTo("COMPLETED")
        assertThat(task["closedBy"].asString()).isEqualTo("LISTENER")

        val history = call(HttpMethod.GET, "/api/tasks/$key/history").json().nodes().map { it["eventType"].asString() }
        assertThat(history).containsExactly("CREATING", "ASSIGNING", "UPDATING", "COMPLETING")
    }

    @Test
    fun `marks a task canceled by an interrupting boundary event and refuses to complete it`() {
        val key = startReview("DOC-3")
        call(HttpMethod.POST, "/api/tasks/$key/assignment")

        camunda.newCorrelateMessageCommand().messageName("ReviewWithdrawn").correlationKey("DOC-3").send().join()

        await.atMost(Duration.ofSeconds(30)) untilAsserted {
            assertThat(call(HttpMethod.GET, "/api/tasks/$key").json()["state"].asString()).isEqualTo("CANCELED")
        }
        val attempt = call(HttpMethod.POST, "/api/tasks/$key/completion", body = mapOf("data" to mapOf("approved" to true)))
        assertThat(attempt.status).isEqualTo(409)
        assertThat(attempt.json()["code"].asString()).isEqualTo("TASK_NOT_ACTIVE")
    }

    @Test
    fun `needs a demo user for user specific calls`() {
        val key = startReview("DOC-5")

        assertThat(call(HttpMethod.GET, "/api/tasks/$key/form", user = null).status).isEqualTo(401)
        assertThat(call(HttpMethod.GET, "/api/tasks/$key/form", user = "mallory").status).isEqualTo(401)
        assertThat(call(HttpMethod.GET, "/api/tasks?assignment=MINE", user = null).status).isEqualTo(401)
    }

    /** Starts a review and returns the key of its user task once the projection has it. */
    private fun startReview(documentId: String): String {
        val instance = camunda.newCreateInstanceCommand()
            .bpmnProcessId("review-process")
            .latestVersion()
            .businessId(documentId)
            .variables(mapOf("documentId" to documentId, "subject" to "Document $documentId"))
            .send()
            .join()
        CamundaAssert.assertThat(instance).isActive()
        var key = ""
        await.atMost(Duration.ofSeconds(30)) untilAsserted {
            val items = call(HttpMethod.GET, "/api/tasks?q=$documentId").json()["items"]
            assertThat(items.size()).isEqualTo(1)
            key = items[0]["userTaskKey"].asString()
        }
        return key
    }

    private fun call(method: HttpMethod, path: String, user: String? = "alice", body: Any? = null): Response {
        val request = api.method(method).uri(path)
        user?.let { request.header("X-Demo-User", it) }
        body?.let { request.contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(it)) }
        return request.exchange { _, response -> Response(response.statusCode.value(), response.bodyTo(String::class.java)) }
    }

    private fun JsonNode.nodes(): List<JsonNode> = (this as Iterable<JsonNode>).toList()

    private fun JsonNode.strings(): List<String> = nodes().map { it.asString() }

    private inner class Response(val status: Int, val body: String?) {
        fun json(): JsonNode = mapper.readTree(body ?: "null")
    }

    companion object {
        private val port = ServerSocket(0).use { it.localPort }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("server.port") { port }
            registry.add("taskmanager.applications.review.label") { "Reviews" }
            registry.add("taskmanager.applications.review.base-url") { "http://localhost:$port" }
            registry.add("taskmanager.applications.review.token") { "review-test-token" }
            registry.add("tasklist.provider.token") { "review-test-token" }
            registry.add("taskmanager.reconciler.interval") { "1h" }
        }
    }
}

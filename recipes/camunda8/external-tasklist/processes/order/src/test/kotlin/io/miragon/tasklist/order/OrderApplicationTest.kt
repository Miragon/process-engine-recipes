package io.miragon.tasklist.order

import io.miragon.tasklist.contract.TasklistApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path

/** The web endpoints that need no engine: the description of the orders API and the forms bundle. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        // Nothing is deployed and no job worker polls, so the tests run without Camunda.
        "camunda.client.deployment.enabled=false",
        "camunda.client.worker.defaults.enabled=false",
    ],
)
@Import(PostgresContainer::class)
class OrderApplicationTest(
    @Autowired private val mapper: JsonMapper,
    @LocalServerPort port: Int,
) {
    private val app = AppClient("http://localhost:$port", mapper)

    /**
     * Keeps `docs/api/order-app.json` in step with the code. After a deliberate change of the orders API,
     * rewrite it with `-Dapi.snapshot.update=true`.
     */
    @Test
    fun `describes the orders API as documented in docs-api`() {
        val actual = normalized(app.get("/v3/api-docs").json as ObjectNode)
        val snapshot = Path.of("../../docs/api/order-app.json")

        if (System.getProperty("api.snapshot.update").toBoolean()) {
            Files.createDirectories(snapshot.parent)
            Files.writeString(snapshot, actual)
        }

        assertThat(snapshot).describedAs("Create it with -Dapi.snapshot.update=true").exists()
        assertThat(Files.readString(snapshot)).isEqualTo(actual)
    }

    @Test
    fun `serves the forms bundle from the jar with the frame's content security policy`() {
        val frame = app.provider("/tasklist/v1/forms/check-order@1/frame")

        assertThat(frame.status).isEqualTo(200)
        assertThat(frame.headers.contentType?.isCompatibleWith(MediaType.TEXT_HTML)).isTrue()
        assertThat(frame.headers.getFirst("Content-Security-Policy")).isEqualTo(TasklistApi.FRAME_CSP)
        assertThat(frame.body).contains("<div id=\"root\"></div>", "<script type=\"module\"").doesNotContain(" src=\"/")
        assertThat(app.provider("/tasklist/v1/forms/start-order@1/frame").status).isEqualTo(200)
        // Prepare delivery is a JSON Forms form, which the tasklist renders itself.
        assertThat(app.provider("/tasklist/v1/forms/prepare-delivery@1/frame").status).isEqualTo(404)
        assertThat(app.get("/tasklist/v1/forms/check-order@1/frame").status).isEqualTo(401)
        assertThat(frame.headers.getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache")
    }

    /** Without the server address, which changes with every run, and with sorted keys, so diffs stay small. */
    private fun normalized(document: ObjectNode): String {
        document.remove("servers")
        val sorted = mapper.rebuild()
            .enable(SerializationFeature.INDENT_OUTPUT, SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build()
        return sorted.writeValueAsString(mapper.treeToValue(document, Map::class.java)) + "\n"
    }
}

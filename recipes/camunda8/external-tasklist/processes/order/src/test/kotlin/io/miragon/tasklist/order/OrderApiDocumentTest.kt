package io.miragon.tasklist.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path

/**
 * Keeps `docs/api/order-app.json` in step with the code. After a deliberate change of the orders API,
 * rewrite it with `-Dapi.snapshot.update=true`.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        // Describing the API needs no engine: nothing is deployed and no job worker polls.
        "camunda.client.deployment.enabled=false",
        "camunda.client.worker.defaults.enabled=false",
    ],
)
@Import(PostgresContainer::class)
class OrderApiDocumentTest(
    @Autowired private val mapper: JsonMapper,
    @LocalServerPort port: Int,
) {
    private val app = AppClient("http://localhost:$port", mapper)

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

    /** Without the server address, which changes with every run, and with sorted keys, so diffs stay small. */
    private fun normalized(document: ObjectNode): String {
        document.remove("servers")
        val sorted = mapper.rebuild()
            .enable(SerializationFeature.INDENT_OUTPUT, SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build()
        return sorted.writeValueAsString(mapper.treeToValue(document, Map::class.java)) + "\n"
    }
}

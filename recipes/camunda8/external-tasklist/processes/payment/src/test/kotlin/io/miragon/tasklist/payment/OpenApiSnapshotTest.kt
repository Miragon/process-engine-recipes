package io.miragon.tasklist.payment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.JsonNodeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path

/**
 * Keeps `docs/api/payment-app.json` in step with the code. After an intended API change,
 * run `./mvnw -f processes/payment/pom.xml verify -Dapi.snapshot.update=true` and commit the file.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // The document needs no Camunda: deploy nothing and poll for no jobs.
    properties = ["camunda.client.deployment.enabled=false", "camunda.client.worker.defaults.enabled=false"],
)
@Import(PostgresContainer::class)
class OpenApiSnapshotTest {

    @LocalServerPort
    private var port = 0

    @Test
    fun `the OpenAPI document matches the snapshot`() {
        val document = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
            .get().uri("/v3/api-docs").exchange()
            .expectStatus().isOk()
            .returnResult(String::class.java).responseBody!!
        val actual = canonical(document)

        if (System.getProperty("api.snapshot.update").toBoolean()) {
            Files.createDirectories(SNAPSHOT.parent)
            Files.writeString(SNAPSHOT, actual)
        }

        assertThat(SNAPSHOT).describedAs("Create the snapshot with -Dapi.snapshot.update=true").exists()
        assertThat(Files.readString(SNAPSHOT).replace("\r\n", "\n"))
            .describedAs("The API differs from %s. If the change is intended, run with -Dapi.snapshot.update=true", SNAPSHOT)
            .isEqualTo(actual)
    }

    /** Without the server URL, which names the random port, and with sorted keys, so the file changes only with the API. */
    private fun canonical(document: String): String {
        val tree = MAPPER.readTree(document) as ObjectNode
        tree.remove("servers")
        return MAPPER.writeValueAsString(tree).replace("\r\n", "\n") + "\n"
    }

    private companion object {
        val SNAPSHOT: Path = Path.of("../../docs/api/payment-app.json")
        val MAPPER: JsonMapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(JsonNodeFeature.WRITE_PROPERTIES_SORTED)
            .build()
    }
}

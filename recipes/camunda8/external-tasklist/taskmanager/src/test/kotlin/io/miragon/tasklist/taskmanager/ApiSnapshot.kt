package io.miragon.tasklist.taskmanager

import org.assertj.core.api.Assertions.assertThat
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * Keeps the OpenAPI document in `docs/api/` in sync with the code: the test fails when the API changed.
 * Refresh the file with `./mvnw verify -Dapi.snapshot.update=true`.
 */
object ApiSnapshot {

    fun verify(apiDocs: String, fileName: String, mapper: JsonMapper) {
        val file = Path.of("../docs/api", fileName)
        val actual = normalize(apiDocs, mapper)
        if (System.getProperty("api.snapshot.update") == "true") {
            Files.createDirectories(file.parent)
            Files.writeString(file, actual)
        }
        assertThat(file).describedAs("Create it with ./mvnw verify -Dapi.snapshot.update=true").exists()
        assertThat(actual).describedAs("The API changed; refresh $file with -Dapi.snapshot.update=true").isEqualTo(Files.readString(file))
    }

    private fun normalize(apiDocs: String, mapper: JsonMapper): String {
        val document = mapper.readValue(apiDocs, LinkedHashMap::class.java)
        document.remove("servers")
        return mapper.rebuild()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build()
            .writeValueAsString(document) + "\n"
    }
}

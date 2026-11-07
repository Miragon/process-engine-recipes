package io.miragon.tasklist.order

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Only the package `engine.camunda` knows Camunda; the rest of the application uses the Process Engine API. */
class EngineBoundaryTest {

    @Test
    fun `only the engine adapter imports Camunda`() {
        val sources = Path.of("src/main/kotlin")
        val adapter = sources.resolve("io/miragon/tasklist/order/engine/camunda")
        val coupled = Files.walk(sources).use { files ->
            files.filter { it.toString().endsWith(".kt") && !it.startsWith(adapter) }
                .filter { file -> Files.readAllLines(file).any { it.startsWith("import io.camunda.") } }
                .map { sources.relativize(it).toString() }
                .toList()
        }
        assertThat(coupled).isEmpty()
    }
}

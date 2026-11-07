package io.miragon.tasklist.payment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Only the package `engine.camunda` knows Camunda and the C8 adapter; the rest of the application uses the Process Engine API. */
class EngineBoundaryTest {

    @Test
    fun `only the engine adapter imports Camunda and the C8 adapter`() {
        val sources = Path.of("src/main/kotlin")
        val adapter = sources.resolve("io/miragon/tasklist/payment/engine/camunda")
        val coupled = Files.walk(sources).use { files ->
            files.filter { it.toString().endsWith(".kt") && !it.startsWith(adapter) }
                .filter { file -> Files.readAllLines(file).any { line -> CAMUNDA.any { line.startsWith("import $it") } } }
                .map { sources.relativize(it).toString() }
                .toList()
        }
        assertThat(coupled).isEmpty()
    }

    private companion object {
        val CAMUNDA = listOf("io.camunda.", "dev.bpmcrafters.processengineapi.adapter.")
    }
}

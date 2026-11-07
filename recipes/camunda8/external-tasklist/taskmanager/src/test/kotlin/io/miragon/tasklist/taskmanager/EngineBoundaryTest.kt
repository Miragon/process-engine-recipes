package io.miragon.tasklist.taskmanager

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Only the package `camunda` knows Camunda; projection, API and reconciler reach it through their ports. */
class EngineBoundaryTest {

    @Test
    fun `only the Camunda adapter imports Camunda`() {
        val sources = Path.of("src/main/kotlin")
        val adapter = sources.resolve("io/miragon/tasklist/taskmanager/camunda")
        val coupled = Files.walk(sources).use { files ->
            files.filter { it.toString().endsWith(".kt") && !it.startsWith(adapter) }
                .filter { file -> Files.readAllLines(file).any { it.startsWith("import io.camunda.") } }
                .map { sources.relativize(it).toString() }
                .toList()
        }
        assertThat(coupled).isEmpty()
    }
}

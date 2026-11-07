package io.miragon.tasklist.taskmanager

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("taskmanager")
data class TaskmanagerProperties(
    val listener: Listener = Listener(),
    val providerTimeout: Duration = Duration.ofSeconds(25),
    val reconciler: Reconciler = Reconciler(),
    /** Registry of the process applications, keyed by the `domain` header of their user tasks. */
    val applications: Map<String, Application> = emptyMap(),
    val users: Map<String, DemoUser> = emptyMap(),
) {
    data class Listener(
        val id: String = "taskmanager-projection",
        val retries: Int = 10,
        val priority: Int = 0,
    ) {
        /** Job type of the listener; a constant because `@JobWorker` does not resolve placeholders. */
        val type get() = TYPE

        companion object {
            const val TYPE = "taskmanager.projection"
        }
    }

    data class Reconciler(
        val interval: Duration = Duration.ofSeconds(30),
        /** Only tasks untouched for this long are checked, so Camunda's search has caught up. */
        val lag: Duration = Duration.ofSeconds(10),
        /** A task Camunda's search does not know after this long is treated as canceled. */
        val missingGrace: Duration = Duration.ofMinutes(30),
        val batchSize: Int = 100,
    )

    data class Application(val label: String, val baseUrl: URI, val token: String)

    data class DemoUser(val name: String, val groups: List<String> = emptyList())
}

package io.miragon.tasklist.taskmanager.applications

import io.miragon.tasklist.taskmanager.TaskmanagerProperties
import io.miragon.tasklist.taskmanager.TaskmanagerProperties.Application
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.net.URI

class ApplicationsTest {

    @Test
    fun `lists the applications by label whatever order the configuration has`() {
        val properties = TaskmanagerProperties(
            applications = linkedMapOf(
                "payment" to Application("Payments", URI("http://payment-app"), "payment-token"),
                "order" to Application("Orders", URI("http://order-app"), "order-token"),
            ),
        )

        val applications = Applications(properties, RestClient.builder(), JsonMapper.builder().build())

        assertThat(applications.all().map { it.domain }).containsExactly("order", "payment")
    }
}

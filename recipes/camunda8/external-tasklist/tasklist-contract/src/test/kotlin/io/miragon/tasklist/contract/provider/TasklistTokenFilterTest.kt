package io.miragon.tasklist.contract.provider

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Against a real Tomcat, which maps and routes on the decoded path, with the paths sent exactly as written. */
@SpringBootTest(classes = [TestProviderApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TasklistTokenFilterTest(@LocalServerPort private val port: Int) {

    private val http = HttpClient.newHttpClient()

    @Test
    fun `asks for the token however the path is spelled, and for every version of the API`() {
        assertThat(status("/tasklist/v1/start-forms", token = "test-token")).isEqualTo(200)

        for (path in listOf("/tasklist/v1/start-forms", "/tasklist/v1;x/start-forms", "/%74asklist/v1/start-forms", "/tasklist/v2/start-forms")) {
            assertThat(status(path)).describedAs(path).isEqualTo(401)
            assertThat(status(path, token = "guessed")).describedAs("$path with a wrong token").isEqualTo(401)
        }
    }

    private fun status(path: String, token: String? = null): Int {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET()
        token?.let { request.header("Authorization", "Bearer $it") }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode()
    }
}

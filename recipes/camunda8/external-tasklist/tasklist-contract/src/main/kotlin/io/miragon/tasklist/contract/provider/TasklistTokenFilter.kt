package io.miragon.tasklist.contract.provider

import io.miragon.tasklist.contract.TasklistApi
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * Lets only the task manager call the provider API: it must send the application's bearer token.
 * The browser never sees this token, so it cannot call the application directly.
 */
class TasklistTokenFilter(token: String) : OncePerRequestFilter() {

    private val expected = "Bearer $token".toByteArray()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith(TasklistApi.BASE_PATH + "/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val actual = request.getHeader(HttpHeaders.AUTHORIZATION)?.toByteArray() ?: ByteArray(0)
        if (MessageDigest.isEqual(expected, actual)) {
            chain.doFilter(request, response)
            return
        }
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.write(
            """{"type":"about:blank","title":"Unauthorized","status":401,""" +
                """"detail":"Only the task manager may call this API.","code":"UNAUTHORIZED"}""",
        )
    }
}

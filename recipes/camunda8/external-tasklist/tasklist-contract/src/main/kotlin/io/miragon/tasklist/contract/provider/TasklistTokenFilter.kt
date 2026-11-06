package io.miragon.tasklist.contract.provider

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
 *
 * The registration decides which requests it sees, on the decoded path the servlet container also routes on.
 * Checking the raw request URI here as well would let `/tasklist/v1;x/...` or `/%74asklist/v1/...` through.
 */
class TasklistTokenFilter(token: String) : OncePerRequestFilter() {

    private val expected = "Bearer $token".toByteArray()

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

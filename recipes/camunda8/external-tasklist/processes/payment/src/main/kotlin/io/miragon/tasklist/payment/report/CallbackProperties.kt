package io.miragon.tasklist.payment.report

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

/**
 * How the application reports payment results to the callback URL of the requesting service.
 *
 * @property allowedHosts the only hosts a callback URL may name. Callers choose the URL, so without this list
 *   they could make the application send requests into the internal network (server-side request forgery).
 * @property retryBackoff the wait before the first retry of a failed report; it doubles with every attempt.
 */
@ConfigurationProperties("payment.callback")
data class CallbackProperties(
    val allowedHosts: Set<String> = emptySet(),
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(10),
    val retryBackoff: Duration = Duration.ofSeconds(5),
) {
    /** Accepts only an absolute http(s) URL without user info whose host is on the allow-list. */
    fun allows(callbackUrl: String): Boolean {
        val uri = runCatching { URI(callbackUrl) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") &&
            uri.rawUserInfo == null &&
            allowedHosts.any { it.equals(uri.host, ignoreCase = true) }
    }
}

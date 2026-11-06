package io.miragon.tasklist.contract.provider

import io.miragon.tasklist.contract.TasklistApi
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * The response of an HTML form frame with its security headers. The application and the task manager both use it,
 * so the frame never runs with weaker headers on the tasklist's origin than the application intended.
 */
fun <T : Any> formFrame(body: T): ResponseEntity<T> =
    ResponseEntity.ok()
        .header("Content-Security-Policy", TasklistApi.FRAME_CSP)
        .header("X-Content-Type-Options", "nosniff")
        .header("Referrer-Policy", "no-referrer")
        .cacheControl(CacheControl.noCache())
        .contentType(MediaType.TEXT_HTML)
        .body(body)

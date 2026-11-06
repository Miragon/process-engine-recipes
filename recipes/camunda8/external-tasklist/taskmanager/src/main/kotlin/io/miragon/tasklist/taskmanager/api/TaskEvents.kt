package io.miragon.tasklist.taskmanager.api

import io.miragon.tasklist.taskmanager.projection.TaskChanged
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pushes every committed projection change to the open tasklists, so a canceled task turns read-only at once.
 * The events only carry keys and states; the tasklist reloads what it shows. One instance only:
 * several task manager instances would need a shared channel such as PostgreSQL NOTIFY.
 */
@RestController
class TaskEvents {

    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    @GetMapping("/api/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(): SseEmitter {
        val emitter = SseEmitter(0L)
        emitters += emitter
        emitter.onCompletion { emitters -= emitter }
        emitter.onTimeout { emitters -= emitter }
        emitter.onError { emitters -= emitter }
        emitter.send(SseEmitter.event().comment("connected"))
        return emitter
    }

    @TransactionalEventListener
    fun onTaskChanged(change: TaskChanged) {
        broadcast(
            SseEmitter.event()
                .name("task-changed")
                .data(TaskChangedDto(change.userTaskKey.toString(), change.state, change.domain), MediaType.APPLICATION_JSON),
        )
    }

    @Scheduled(fixedRate = 20_000)
    fun keepAlive() = broadcast(SseEmitter.event().comment("keep-alive"))

    private fun broadcast(event: SseEmitter.SseEventBuilder) {
        emitters.forEach { emitter ->
            try {
                emitter.send(event)
            } catch (e: Exception) {
                emitters -= emitter
            }
        }
    }
}

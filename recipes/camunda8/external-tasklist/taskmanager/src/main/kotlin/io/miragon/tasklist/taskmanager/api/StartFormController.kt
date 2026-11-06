package io.miragon.tasklist.taskmanager.api

import io.miragon.tasklist.contract.FormView
import io.miragon.tasklist.contract.StartFormRequest
import io.miragon.tasklist.contract.StartResult
import io.miragon.tasklist.contract.StartSubmission
import io.miragon.tasklist.contract.UserContext
import io.miragon.tasklist.contract.provider.formFrame
import io.miragon.tasklist.taskmanager.applications.Applications
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class StartFormController(private val applications: Applications) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Start forms of all applications. An application that is down is left out rather than failing the list. */
    @GetMapping("/start-forms")
    fun startForms(): List<StartFormEntry> =
        applications.all().flatMap { app ->
            try {
                app.startForms().map { StartFormEntry(app.domain, app.label, it.id, it.formRef, it.label, it.description) }
            } catch (e: ApiProblem) {
                log.warn("Start forms of {} unavailable: {}", app.domain, e.message)
                emptyList()
            }
        }

    @GetMapping("/start-forms/{domain}/{id}/form")
    fun startForm(@PathVariable domain: String, @PathVariable id: String, user: UserContext): FormView =
        applications.client(domain).startForm(id, StartFormRequest(user))

    @PostMapping("/start-forms/{domain}/{id}/submission")
    fun submit(
        @PathVariable domain: String,
        @PathVariable id: String,
        user: UserContext,
        @RequestBody body: SubmissionBody,
    ): ResponseEntity<StartResult> =
        ResponseEntity.status(HttpStatus.CREATED).body(applications.client(domain).submit(id, StartSubmission(user, body.data)))

    /**
     * The micro-frontend of an application, served on the tasklist's origin for the sandboxed iframe.
     * The task manager sets the frame's security headers itself instead of trusting the application's.
     */
    @GetMapping("/forms/{domain}/{formRef}/frame", produces = [MediaType.TEXT_HTML_VALUE])
    fun frame(@PathVariable domain: String, @PathVariable formRef: String): ResponseEntity<ByteArray> =
        formFrame(applications.client(domain).frame(formRef))
}

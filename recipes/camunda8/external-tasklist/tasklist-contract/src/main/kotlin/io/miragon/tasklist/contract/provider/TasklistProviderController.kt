package io.miragon.tasklist.contract.provider

import io.miragon.tasklist.contract.BadTasklistRequest
import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.FormKind
import io.miragon.tasklist.contract.FormNotFound
import io.miragon.tasklist.contract.FormView
import io.miragon.tasklist.contract.StartFormDescriptor
import io.miragon.tasklist.contract.StartFormRequest
import io.miragon.tasklist.contract.StartResult
import io.miragon.tasklist.contract.StartSubmission
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.TaskSubmission
import io.miragon.tasklist.contract.TasklistApi
import io.miragon.tasklist.contract.ValidationFailed
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Dispatches the provider API to the form handlers of the application. */
@RestController
class TasklistProviderController(
    taskHandlers: List<TaskFormHandler<*>>,
    startHandlers: List<StartFormHandler<*>>,
    private val properties: TasklistProviderProperties,
    private val mapper: JsonMapper,
) {
    private val tasks = taskHandlers.associateBy { it.formRef }
    private val starts = startHandlers.associateBy { it.descriptor.id }

    @PostMapping(TasklistApi.TASK_FORM)
    fun taskForm(@PathVariable userTaskKey: String, @RequestBody context: TaskContext): FormView =
        handlerFor(userTaskKey, context).view(context)

    @PostMapping(TasklistApi.TASK_DRAFT)
    fun draft(@PathVariable userTaskKey: String, @RequestBody submission: TaskSubmission): ResponseEntity<Void> {
        handlerFor(userTaskKey, submission.context).draftWith(submission)
        return ResponseEntity.noContent().build()
    }

    @PostMapping(TasklistApi.TASK_COMPLETION)
    fun complete(@PathVariable userTaskKey: String, @RequestBody submission: TaskSubmission): ResponseEntity<Void> =
        when (handlerFor(userTaskKey, submission.context).completeWith(submission)) {
            CompletionOutcome.COMPLETED -> ResponseEntity.noContent().build()
            CompletionOutcome.PENDING -> ResponseEntity.accepted().build()
        }

    @GetMapping(TasklistApi.START_FORMS)
    fun startForms(): List<StartFormDescriptor> = starts.values.map { it.descriptor }

    @PostMapping(TasklistApi.START_FORM)
    fun startForm(@PathVariable id: String, @RequestBody request: StartFormRequest): FormView =
        startHandler(id).view(request.user)

    @PostMapping(TasklistApi.START_SUBMISSION)
    fun submit(@PathVariable id: String, @RequestBody submission: StartSubmission): ResponseEntity<StartResult> =
        ResponseEntity.status(HttpStatus.CREATED).body(startHandler(id).submitWith(submission))

    @GetMapping(TasklistApi.FORM_FRAME, produces = [MediaType.TEXT_HTML_VALUE])
    fun frame(@PathVariable formRef: String): ResponseEntity<Resource> {
        val bundle = properties.frameBundle
        if (!isHtmlForm(formRef) || bundle == null || !bundle.exists()) throw FormNotFound(formRef)
        return ResponseEntity.ok()
            .header("Content-Security-Policy", TasklistApi.FRAME_CSP)
            .header("X-Content-Type-Options", "nosniff")
            .header("Referrer-Policy", "no-referrer")
            .cacheControl(CacheControl.noCache())
            .contentType(MediaType.TEXT_HTML)
            .body(bundle)
    }

    private fun handlerFor(userTaskKey: String, context: TaskContext): TaskFormHandler<*> {
        if (userTaskKey != context.userTaskKey) throw BadTasklistRequest("The path and the context name different user tasks.")
        return tasks[context.formRef] ?: throw FormNotFound(context.formRef)
    }

    private fun startHandler(id: String): StartFormHandler<*> = starts[id] ?: throw FormNotFound(id)

    private fun isHtmlForm(formRef: String): Boolean =
        tasks[formRef]?.kind == FormKind.HTML ||
            starts.values.any { it.descriptor.formRef == formRef && it.kind == FormKind.HTML }

    private fun <D : Any> TaskFormHandler<D>.draftWith(submission: TaskSubmission) =
        draft(submission.context, bind(submission.data, submissionType))

    private fun <D : Any> TaskFormHandler<D>.completeWith(submission: TaskSubmission) =
        complete(submission.context, bind(submission.data, submissionType))

    private fun <D : Any> StartFormHandler<D>.submitWith(submission: StartSubmission) =
        submit(submission.user, bind(submission.data, submissionType))

    /** Binds the submitted data to the handler's type and silently drops fields the form may not change. */
    private fun <D : Any> bind(data: Map<String, Any?>, type: Class<D>): D =
        try {
            mapper.readerFor(type)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(mapper.valueToTree<JsonNode>(data))
        } catch (e: RuntimeException) {
            throw ValidationFailed("The form data does not match the form: ${e.message?.lineSequence()?.first()}")
        }
}

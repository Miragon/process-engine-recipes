package io.miragon.tasklist.taskmanager.applications

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.miragon.tasklist.contract.FieldError
import io.miragon.tasklist.contract.FormView
import io.miragon.tasklist.contract.StartFormDescriptor
import io.miragon.tasklist.contract.StartFormRequest
import io.miragon.tasklist.contract.StartResult
import io.miragon.tasklist.contract.StartSubmission
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.TaskSubmission
import io.miragon.tasklist.contract.TasklistApi
import io.miragon.tasklist.taskmanager.TaskmanagerProperties
import io.miragon.tasklist.taskmanager.api.ApiProblem
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration

/**
 * Registry of the process applications. The `domain` header of a user task names its application;
 * the task manager only forwards to applications configured here.
 */
@Component
class Applications(
    private val properties: TaskmanagerProperties,
    restClient: RestClient.Builder,
    mapper: JsonMapper,
) {
    private val clients: Map<String, ProviderClient> = properties.applications.mapValues { (domain, app) ->
        val requestFactory = JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
        ).apply { setReadTimeout(properties.providerTimeout) }
        val client = restClient.clone()
            .baseUrl(app.baseUrl.toString())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${app.token}")
            .requestFactory(requestFactory)
            .build()
        ProviderClient(domain, app.label, client, mapper)
    }

    fun client(domain: String): ProviderClient =
        clients[domain] ?: throw ApiProblem(502, "UNKNOWN_APPLICATION", "No application is registered for the domain '$domain'.")

    fun label(domain: String): String = properties.applications[domain]?.label ?: domain

    fun all(): List<ProviderClient> = clients.values.toList()
}

/** Calls the provider API (`/tasklist/v1`) of one application and passes its problems on unchanged. */
class ProviderClient(
    val domain: String,
    val label: String,
    private val rest: RestClient,
    private val mapper: JsonMapper,
) {
    fun form(context: TaskContext): FormView = call {
        rest.post().uri(TasklistApi.TASK_FORM, context.userTaskKey).body(context).retrieve().body(FormView::class.java)!!
    }

    fun draft(submission: TaskSubmission) = call {
        rest.post().uri(TasklistApi.TASK_DRAFT, submission.context.userTaskKey).body(submission).retrieve().toBodilessEntity()
    }

    /** Returns 204 when the task is completed, 202 when Camunda has not confirmed it yet. */
    fun complete(submission: TaskSubmission): Int = call {
        rest.post().uri(TasklistApi.TASK_COMPLETION, submission.context.userTaskKey).body(submission)
            .retrieve().toBodilessEntity().statusCode.value()
    }

    fun startForms(): List<StartFormDescriptor> = call {
        rest.get().uri(TasklistApi.START_FORMS).retrieve().body(object : ParameterizedTypeReference<List<StartFormDescriptor>>() {})!!
    }

    fun startForm(id: String, request: StartFormRequest): FormView = call {
        rest.post().uri(TasklistApi.START_FORM, id).body(request).retrieve().body(FormView::class.java)!!
    }

    fun submit(id: String, submission: StartSubmission): StartResult = call {
        rest.post().uri(TasklistApi.START_SUBMISSION, id).body(submission).retrieve().body(StartResult::class.java)!!
    }

    fun frame(formRef: String): ByteArray = call {
        rest.get().uri(TasklistApi.FORM_FRAME, formRef).retrieve().body(ByteArray::class.java)!!
    }

    private fun <T> call(block: () -> T): T =
        try {
            block()
        } catch (e: RestClientResponseException) {
            throw translate(e)
        } catch (e: ResourceAccessException) {
            throw ApiProblem(503, "APPLICATION_UNAVAILABLE", "The $label application does not answer. Try again in a moment.", retryAfterSeconds = 5)
        }

    private fun translate(e: RestClientResponseException): ApiProblem {
        val status = e.statusCode.value()
        val body = runCatching { mapper.readValue(e.responseBodyAsString, ProviderProblemBody::class.java) }.getOrNull()
        return when {
            status == 401 -> ApiProblem(502, "APPLICATION_REJECTED_TOKEN", "The $label application rejected the task manager's token.")
            status == 503 -> ApiProblem(503, body?.code ?: "UNAVAILABLE", body?.detail ?: "The $label application is not ready. Try again in a moment.", retryAfterSeconds = 5)
            status >= 500 -> ApiProblem(502, "APPLICATION_ERROR", "The $label application failed: ${body?.detail ?: e.statusText}")
            else -> ApiProblem(status, body?.code ?: "APPLICATION_REJECTED", body?.detail ?: e.statusText, body?.errors.orEmpty())
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ProviderProblemBody(
    val detail: String? = null,
    val code: String? = null,
    val errors: List<FieldError>? = null,
)

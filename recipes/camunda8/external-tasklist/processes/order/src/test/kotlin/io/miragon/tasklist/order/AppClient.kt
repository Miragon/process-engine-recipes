package io.miragon.tasklist.order

import io.camunda.client.api.search.response.UserTask
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.contract.UserContext
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.MissingNode

/** Calls the running application over HTTP, as the task manager and the payment application do. */
class AppClient(baseUrl: String, private val mapper: JsonMapper) {

    inner class Answer(val status: Int, val headers: HttpHeaders, val body: String) {
        val json: JsonNode by lazy { if (body.isEmpty()) MissingNode.getInstance() else mapper.readTree(body) }
    }

    private val http = RestClient.create(baseUrl)

    fun get(path: String, token: String? = null): Answer = call(http.get().uri(path), token)

    fun post(path: String, body: Any?, token: String? = null): Answer =
        call(
            http.post().uri(path).apply {
                if (body != null) contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(body))
            },
            token,
        )

    /** The provider API, called with the token only the task manager knows. */
    fun provider(path: String, body: Any? = null): Answer =
        if (body == null) get(path, TOKEN) else post(path, body, TOKEN)

    private fun call(request: RestClient.RequestHeadersSpec<*>, token: String?): Answer {
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        return request.exchange { _, response ->
            Answer(response.statusCode.value(), response.headers, response.body.readAllBytes().decodeToString())
        }!!
    }

    companion object {
        const val TOKEN = "order-demo-token"

        /**
         * The context the task manager would send for [task]. It lets only the assignee work on a task,
         * so the user has claimed it.
         */
        fun contextOf(task: UserTask, user: UserContext, assignee: String? = user.id) = TaskContext(
            userTaskKey = task.userTaskKey.toString(),
            processInstanceKey = task.processInstanceKey.toString(),
            processDefinitionKey = task.processDefinitionKey.toString(),
            processDefinitionId = task.bpmnProcessId,
            processDefinitionVersion = task.processDefinitionVersion,
            elementId = task.elementId,
            formRef = task.externalFormReference,
            businessId = task.businessId,
            state = TaskState.OPEN,
            assignee = assignee,
            candidateGroups = task.candidateGroups,
            tenantId = task.tenantId,
            user = user,
        )
    }
}

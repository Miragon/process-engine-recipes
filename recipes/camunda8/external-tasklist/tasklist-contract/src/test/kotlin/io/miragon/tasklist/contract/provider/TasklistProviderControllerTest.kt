package io.miragon.tasklist.contract.provider

import io.miragon.tasklist.contract.FormView
import io.miragon.tasklist.contract.HtmlFormView
import io.miragon.tasklist.contract.JsonFormsView
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.TaskState
import io.miragon.tasklist.contract.TaskSubmission
import io.miragon.tasklist.contract.TasklistApi
import io.miragon.tasklist.contract.UserContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(classes = [TestProviderApplication::class])
@AutoConfigureMockMvc
class TasklistProviderControllerTest(
    @Autowired private val mvc: MockMvcTester,
    @Autowired private val mapper: JsonMapper,
    @Autowired private val reviewForm: ReviewForm,
) {
    private val context = TaskContext(
        userTaskKey = "2251799813685337",
        processInstanceKey = "2251799813685328",
        processDefinitionKey = "2251799813685327",
        processDefinitionId = "review-process",
        processDefinitionVersion = 1,
        elementId = "review",
        formRef = "review@1",
        businessId = "R-1",
        state = TaskState.OPEN,
        assignee = "alice",
        candidateGroups = listOf("sales"),
        tenantId = "<default>",
        user = UserContext("alice", listOf("sales")),
    )

    @Test
    fun `rejects calls without the task manager's token`() {
        assertThat(mvc.post().uri(TasklistApi.TASK_FORM, context.userTaskKey).json(context))
            .hasStatus(401)
            .bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `returns the form of the handler named by the form reference`() {
        assertThat(mvc.post().uri(TasklistApi.TASK_FORM, context.userTaskKey).authorized().json(context))
            .hasStatusOk()
            .bodyJson().satisfies({
                it.assertThat().extractingPath("$.type").isEqualTo("json-forms")
                it.assertThat().extractingPath("$.formRef").isEqualTo("review@1")
                it.assertThat().extractingPath("$.readOnly").isEqualTo(false)
            })
    }

    @Test
    fun `binds only the fields of the submission type`() {
        val submission = TaskSubmission(context, mapOf("approved" to true, "amount" to 999, "comment" to "fine"))

        assertThat(mvc.post().uri(TasklistApi.TASK_COMPLETION, context.userTaskKey).authorized().json(submission))
            .hasStatus(204)
        assertThat(reviewForm.lastSubmission).isEqualTo(Review(approved = true, comment = "fine"))
    }

    @Test
    fun `reports validation errors as problem details`() {
        val submission = TaskSubmission(context, mapOf("approved" to false))

        assertThat(mvc.post().uri(TasklistApi.TASK_COMPLETION, context.userTaskKey).authorized().json(submission))
            .hasStatus(422)
            .bodyJson().satisfies({
                it.assertThat().extractingPath("$.code").isEqualTo("VALIDATION_FAILED")
                it.assertThat().extractingPath("$.errors[0].field").isEqualTo("comment")
            })
    }

    @Test
    fun `answers 202 when Camunda has not confirmed the completion yet`() {
        val submission = TaskSubmission(context.copy(formRef = "sign@1"), emptyMap())

        assertThat(mvc.post().uri(TasklistApi.TASK_COMPLETION, context.userTaskKey).authorized().json(submission))
            .hasStatus(202)
    }

    @Test
    fun `answers 404 for an unknown form and 405 for drafts of a form without drafts`() {
        assertThat(mvc.post().uri(TasklistApi.TASK_FORM, context.userTaskKey).authorized().json(context.copy(formRef = "review@2")))
            .hasStatus(404)
            .bodyJson().extractingPath("$.code").isEqualTo("FORM_NOT_FOUND")
        assertThat(mvc.post().uri(TasklistApi.TASK_DRAFT, context.userTaskKey).authorized().json(TaskSubmission(context, emptyMap())))
            .hasStatus(405)
    }

    @Test
    fun `rejects a context for another task than the path`() {
        assertThat(mvc.post().uri(TasklistApi.TASK_FORM, "1").authorized().json(context))
            .hasStatus(400)
    }

    @Test
    fun `serves the frame bundle with its own content security policy, only for HTML forms`() {
        assertThat(mvc.get().uri(TasklistApi.FORM_FRAME, "sign@1").authorized())
            .hasStatusOk()
            .hasHeader("Content-Security-Policy", TasklistApi.FRAME_CSP)
            .hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
        assertThat(mvc.get().uri(TasklistApi.FORM_FRAME, "review@1").authorized())
            .hasStatus(404)
    }

    @Test
    fun `lists start forms and starts a process`() {
        assertThat(mvc.get().uri(TasklistApi.START_FORMS).authorized())
            .hasStatusOk()
            .bodyJson().extractingPath("$[0].formRef").isEqualTo("apply@1")
        assertThat(
            mvc.post().uri(TasklistApi.START_SUBMISSION, "apply").authorized()
                .json(mapOf("user" to context.user, "data" to mapOf("comment" to "hi"))),
        )
            .hasStatus(201)
            .bodyJson().extractingPath("$.businessId").isEqualTo("APP-alice")
    }

    @Test
    fun `reads form views back into their subtype`() {
        val json = mapper.writerFor(Array<FormView>::class.java)
            .writeValueAsString(arrayOf(JsonFormsView("a@1", mapOf("type" to "object")), HtmlFormView("b@1")))

        val views = mapper.readValue(json, Array<FormView>::class.java)

        assertThat(json).contains("\"type\":\"json-forms\"", "\"type\":\"html\"")
        assertThat(views.map { it::class }).containsExactly(JsonFormsView::class, HtmlFormView::class)
    }

    private fun MockMvcTester.MockMvcRequestBuilder.authorized() = header("Authorization", "Bearer test-token")

    private fun MockMvcTester.MockMvcRequestBuilder.json(body: Any) =
        contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))
}

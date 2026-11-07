package io.miragon.tasklist.taskmanager

import dev.bpmcrafters.processengineapi.adapter.c8.task.completion.C8CamundaClientUserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import io.camunda.client.CamundaClient
import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.FormKind
import io.miragon.tasklist.contract.JsonFormsView
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.ValidationFailed
import io.miragon.tasklist.contract.provider.EnableTasklistProvider
import io.miragon.tasklist.contract.provider.TaskFormHandler
import io.miragon.tasklist.contract.provider.completeUserTask
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/** A process application inside the test: it owns the form `review@1` of `review.bpmn` and completes its tasks. */
@TestConfiguration(proxyBeanMethods = false)
@EnableTasklistProvider
class ReviewProvider {

    /** Like the process applications, it completes through the Process Engine API and its C8 adapter. */
    @Bean
    fun reviewForm(camunda: CamundaClient) = ReviewForm(C8CamundaClientUserTaskCompletionApiImpl(camunda, InMemSubscriptionRepository()))
}

class ReviewForm(private val userTasks: UserTaskCompletionApi) : TaskFormHandler<ReviewDecision> {
    override val formRef = "review@1"
    override val kind = FormKind.JSON_FORMS
    override val submissionType = ReviewDecision::class.java

    override fun view(context: TaskContext) = JsonFormsView(
        formRef = formRef,
        schema = mapOf("type" to "object", "properties" to mapOf("approved" to mapOf("type" to "boolean"))),
        data = mapOf("documentId" to context.businessId),
    )

    override fun complete(context: TaskContext, submission: ReviewDecision): CompletionOutcome {
        val approved = submission.approved ?: throw ValidationFailed("approved", "Decide whether the document is approved.")
        return userTasks.completeUserTask(context.userTaskKey, mapOf("approved" to approved))
    }
}

data class ReviewDecision(val approved: Boolean? = null)

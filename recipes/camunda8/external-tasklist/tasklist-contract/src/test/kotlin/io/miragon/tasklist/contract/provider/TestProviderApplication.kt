package io.miragon.tasklist.contract.provider

import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.FormKind
import io.miragon.tasklist.contract.HtmlFormView
import io.miragon.tasklist.contract.JsonFormsView
import io.miragon.tasklist.contract.StartFormDescriptor
import io.miragon.tasklist.contract.StartResult
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.UserContext
import io.miragon.tasklist.contract.ValidationFailed
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
@EnableTasklistProvider
class TestProviderApplication {

    @Bean
    fun reviewForm() = ReviewForm()

    @Bean
    fun signatureForm() = object : TaskFormHandler<Review> {
        override val formRef = "sign@1"
        override val kind = FormKind.HTML
        override val submissionType = Review::class.java
        override fun view(context: TaskContext) = HtmlFormView(formRef, data = mapOf("subject" to "Contract 7"))
        override fun complete(context: TaskContext, submission: Review) = CompletionOutcome.PENDING
    }

    @Bean
    fun applicationForm() = object : StartFormHandler<Review> {
        override val descriptor = StartFormDescriptor("apply", "apply@1", "Apply")
        override val kind = FormKind.JSON_FORMS
        override val submissionType = Review::class.java
        override fun view(user: UserContext) = JsonFormsView("apply@1", schema = mapOf("type" to "object"))
        override fun submit(user: UserContext, submission: Review) = StartResult("42", "APP-${user.id}")
    }
}

data class Review(val approved: Boolean? = null, val comment: String? = null)

class ReviewForm : TaskFormHandler<Review> {
    override val formRef = "review@1"
    override val kind = FormKind.JSON_FORMS
    override val submissionType = Review::class.java
    var lastSubmission: Review? = null

    override fun view(context: TaskContext) = JsonFormsView(
        formRef = formRef,
        schema = mapOf("type" to "object"),
        data = mapOf("approved" to null),
        readOnly = context.assignee != context.user.id,
    )

    override fun complete(context: TaskContext, submission: Review): CompletionOutcome {
        lastSubmission = submission
        if (submission.approved == false && submission.comment.isNullOrBlank()) {
            throw ValidationFailed("comment", "Give a reason when you decline.")
        }
        return CompletionOutcome.COMPLETED
    }
}

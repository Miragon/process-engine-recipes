package io.miragon.tasklist.contract.provider

import io.miragon.tasklist.contract.CompletionOutcome
import io.miragon.tasklist.contract.DraftNotSupported
import io.miragon.tasklist.contract.FormKind
import io.miragon.tasklist.contract.FormView
import io.miragon.tasklist.contract.StartFormDescriptor
import io.miragon.tasklist.contract.StartResult
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.UserContext

/**
 * One version of one task form of a process application, found by its form reference (`check-order@1`).
 *
 * Submitted data is bound to [submissionType], so only the fields the form may change reach the handler.
 * Throw a [io.miragon.tasklist.contract.TasklistProblem] to reject a submission.
 */
interface TaskFormHandler<D : Any> {
    val formRef: String
    val kind: FormKind
    val submissionType: Class<D>

    fun view(context: TaskContext): FormView

    fun draft(context: TaskContext, submission: D): Unit = throw DraftNotSupported(formRef)

    fun complete(context: TaskContext, submission: D): CompletionOutcome
}

/** A form that starts a process of the application, listed in the tasklist's start catalog. */
interface StartFormHandler<D : Any> {
    val descriptor: StartFormDescriptor
    val kind: FormKind
    val submissionType: Class<D>

    fun view(user: UserContext): FormView

    fun submit(user: UserContext, submission: D): StartResult
}

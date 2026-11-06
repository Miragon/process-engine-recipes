package io.miragon.tasklist.contract

/** Body of the draft and completion calls: the verified context plus what the user entered. */
data class TaskSubmission(
    val context: TaskContext,
    val data: Map<String, Any?>,
)

data class StartFormDescriptor(
    val id: String,
    val formRef: String,
    val label: String,
    val description: String? = null,
)

data class StartFormRequest(
    val user: UserContext,
)

data class StartSubmission(
    val user: UserContext,
    val data: Map<String, Any?>,
)

data class StartResult(
    val processInstanceKey: String,
    val businessId: String?,
)

/** How a completion ended. [PENDING] means Camunda accepted it but did not confirm in time. */
enum class CompletionOutcome { COMPLETED, PENDING }

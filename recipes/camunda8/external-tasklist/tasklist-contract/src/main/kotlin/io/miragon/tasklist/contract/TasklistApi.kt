package io.miragon.tasklist.contract

/** Paths and headers of the provider API that every process application offers to the task manager. */
object TasklistApi {
    const val BASE_PATH = "/tasklist/v1"
    const val TASK_FORM = "$BASE_PATH/tasks/{userTaskKey}/form"
    const val TASK_DRAFT = "$BASE_PATH/tasks/{userTaskKey}/draft"
    const val TASK_COMPLETION = "$BASE_PATH/tasks/{userTaskKey}/completion"
    const val START_FORMS = "$BASE_PATH/start-forms"
    const val START_FORM = "$BASE_PATH/start-forms/{id}/form"
    const val START_SUBMISSION = "$BASE_PATH/start-forms/{id}/submission"
    const val FORM_FRAME = "$BASE_PATH/forms/{formRef}/frame"

    /** Custom header on a user task that names the owning application in the task manager's registry. */
    const val DOMAIN_HEADER = "domain"

    /**
     * Content security policy of an HTML form frame. The frame runs sandboxed in an opaque origin,
     * may only run its own inline bundle, cannot load or send anything and may only be embedded by the tasklist.
     */
    const val FRAME_CSP = "sandbox allow-scripts; default-src 'none'; script-src 'unsafe-inline'; " +
        "style-src 'unsafe-inline'; img-src data:; font-src data:; connect-src 'none'; " +
        "form-action 'none'; base-uri 'none'; frame-ancestors 'self'"
}

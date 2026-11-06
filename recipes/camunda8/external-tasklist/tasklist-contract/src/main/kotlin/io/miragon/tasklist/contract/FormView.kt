package io.miragon.tasklist.contract

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * A form as a process application hands it to the tasklist.
 *
 * The tasklist renders [JsonFormsView] itself and loads [HtmlFormView] as the application's
 * micro-frontend into a sandboxed iframe, passing [data] through the form protocol.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(JsonFormsView::class, name = "json-forms"),
    JsonSubTypes.Type(HtmlFormView::class, name = "html"),
)
sealed interface FormView {
    val formRef: String
    val data: Map<String, Any?>
    val readOnly: Boolean
    val draftable: Boolean
}

data class JsonFormsView(
    override val formRef: String,
    val schema: Map<String, Any?>,
    val uiSchema: Map<String, Any?>? = null,
    override val data: Map<String, Any?> = emptyMap(),
    override val readOnly: Boolean = false,
    override val draftable: Boolean = false,
) : FormView

data class HtmlFormView(
    override val formRef: String,
    override val data: Map<String, Any?> = emptyMap(),
    override val readOnly: Boolean = false,
    override val draftable: Boolean = false,
) : FormView

enum class FormKind { JSON_FORMS, HTML }

package io.miragon.tasklist.taskmanager.api

import io.miragon.tasklist.contract.HtmlFormView
import io.miragon.tasklist.contract.JsonFormsView
import io.miragon.tasklist.contract.UserContext
import io.miragon.tasklist.taskmanager.identity.CurrentUserResolver
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.HeaderParameter
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** What springdoc cannot read from the code, so that clients generated from the API document get it right. */
@Configuration(proxyBeanMethods = false)
class ApiDocumentation {

    init {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UserContext::class.java)
    }

    /** The demo user is the `X-Demo-User` header, not the `UserContext` it is resolved into. */
    @Bean
    fun demoUserHeader(): OperationCustomizer = OperationCustomizer { operation, handlerMethod ->
        handlerMethod.methodParameters.firstOrNull { it.parameterType == UserContext::class.java }?.let { parameter ->
            operation.addParametersItem(
                HeaderParameter()
                    .name(CurrentUserResolver.HEADER)
                    .description("One of the demo users (GET /api/users), for example alice")
                    .required(!parameter.isOptional)
                    .schema(StringSchema()),
            )
        }
        operation
    }

    /** Camunda keys in paths are strings, like everywhere else in the API: they exceed JavaScript's safe integers. */
    @Bean
    fun keysAsStrings(): OperationCustomizer = OperationCustomizer { operation, _ ->
        operation.parameters.orEmpty()
            .filter { it.`in` == "path" && it.schema?.format == "int64" }
            .forEach { it.schema = StringSchema().pattern("^[0-9]+$") }
        operation
    }

    /** The values of `type` that tell the two form views apart on the wire. */
    @Bean
    fun formViewTypes(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        openApi.components?.schemas?.get("FormView")?.discriminator
            ?.mapping("json-forms", "#/components/schemas/${JsonFormsView::class.simpleName}")
            ?.mapping("html", "#/components/schemas/${HtmlFormView::class.simpleName}")
    }
}

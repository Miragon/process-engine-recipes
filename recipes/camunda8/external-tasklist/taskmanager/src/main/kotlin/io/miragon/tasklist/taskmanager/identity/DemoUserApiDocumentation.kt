package io.miragon.tasklist.taskmanager.identity

import io.miragon.tasklist.contract.UserContext
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.HeaderParameter
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Documents the demo user as the `X-Demo-User` header it is, instead of the `UserContext` it is resolved into. */
@Configuration(proxyBeanMethods = false)
class DemoUserApiDocumentation {

    init {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UserContext::class.java)
    }

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
}

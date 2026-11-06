package io.miragon.tasklist.taskmanager.identity

import io.miragon.tasklist.contract.UserContext
import io.miragon.tasklist.taskmanager.TaskmanagerProperties
import io.miragon.tasklist.taskmanager.api.ApiProblem
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * The recipe has no authentication: the tasklist sends one of the configured demo users as `X-Demo-User`.
 * A real deployment takes the user and groups from the identity provider's token instead.
 */
@Component
class DemoUsers(private val properties: TaskmanagerProperties) {

    fun find(id: String?): UserContext? =
        id?.let { properties.users[it] }?.let { UserContext(id, it.groups) }

    fun all(): List<DemoUserView> =
        properties.users.map { (id, user) -> DemoUserView(id, user.name, user.groups) }

    fun nameOf(id: String) = properties.users[id]?.name ?: id
}

data class DemoUserView(val id: String, val name: String, val groups: List<String>)

/** Resolves a `UserContext` parameter of a controller method from the `X-Demo-User` header. */
class CurrentUserResolver(private val users: DemoUsers) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == UserContext::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): UserContext? {
        val header = webRequest.getHeader(HEADER)
        return users.find(header)
            ?: if (header == null && parameter.isOptional) {
                null
            } else {
                throw ApiProblem(401, "UNAUTHORIZED", "Choose one of the demo users and send it as $HEADER.")
            }
    }

    companion object {
        const val HEADER = "X-Demo-User"
    }
}

@Configuration(proxyBeanMethods = false)
class IdentityConfiguration(private val users: DemoUsers) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers += CurrentUserResolver(users)
    }
}

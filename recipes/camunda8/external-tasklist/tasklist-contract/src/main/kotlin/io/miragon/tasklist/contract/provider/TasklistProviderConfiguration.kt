package io.miragon.tasklist.contract.provider

import io.miragon.tasklist.contract.TasklistApi
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.io.Resource

/** Turns a Spring Boot application into a form provider of the central tasklist. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(TasklistProviderConfiguration::class)
annotation class EnableTasklistProvider

/**
 * @property token bearer token the task manager sends; configure the same value there.
 * @property frameBundle the single-file micro-frontend that renders the application's HTML forms.
 */
@ConfigurationProperties("tasklist.provider")
data class TasklistProviderProperties(
    val token: String,
    val frameBundle: Resource? = null,
) {
    init {
        require(token.isNotBlank()) { "Set tasklist.provider.token to the token the task manager uses for this application." }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TasklistProviderProperties::class)
@Import(TasklistProviderController::class, TasklistProviderExceptionHandler::class)
class TasklistProviderConfiguration {

    @Bean
    fun tasklistTokenFilter(properties: TasklistProviderProperties): FilterRegistrationBean<TasklistTokenFilter> =
        FilterRegistrationBean(TasklistTokenFilter(properties.token)).apply {
            addUrlPatterns("${TasklistApi.NAMESPACE}/*")
        }
}

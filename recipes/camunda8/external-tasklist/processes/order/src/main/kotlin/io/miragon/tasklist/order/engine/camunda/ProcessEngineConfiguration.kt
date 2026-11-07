package io.miragon.tasklist.order.engine.camunda

import dev.bpmcrafters.processengineapi.adapter.c8.task.completion.C8CamundaClientUserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import io.camunda.client.CamundaClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Connects the Process Engine API to Camunda 8 with the core of the C8 adapter. This package is the only part of the
 * application that knows Camunda; everything else talks to the engine through the API.
 */
@Configuration(proxyBeanMethods = false)
class ProcessEngineConfiguration {

    @Bean
    fun subscriptionRepository(): SubscriptionRepository = InMemSubscriptionRepository()

    /** Completes in the caller's thread, so a rejected completion rolls back the caller's transaction. */
    @Bean
    fun userTaskCompletionApi(camunda: CamundaClient, subscriptions: SubscriptionRepository): UserTaskCompletionApi =
        C8CamundaClientUserTaskCompletionApiImpl(camunda, subscriptions)
}

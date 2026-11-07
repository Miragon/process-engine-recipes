package io.miragon.tasklist.payment.engine.camunda

import dev.bpmcrafters.processengineapi.adapter.c8.task.completion.C8CamundaClientUserTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c8.task.completion.C8ExternalServiceTaskCompletionApiImpl
import dev.bpmcrafters.processengineapi.adapter.c8.task.completion.FailureRetrySupplier
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.SubscribingServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c8.task.subscription.C8TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import io.camunda.client.CamundaClient
import io.camunda.client.annotation.Deployment
import io.camunda.client.lifecycle.CamundaClientLifecycleAware
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

/**
 * Connects the Process Engine API to Camunda 8 with the core of the C8 adapter. This package is the only part of the
 * application that knows Camunda; everything else talks to the engine through the API.
 */
@Configuration(proxyBeanMethods = false)
@Deployment(resources = ["classpath*:/bpmn/*.bpmn"])
class ProcessEngineConfiguration {

    @Bean
    fun subscriptionRepository(): SubscriptionRepository = InMemSubscriptionRepository()

    @Bean
    fun startProcessApi(camunda: CamundaClient): StartProcessApi = C8StartProcessApi(camunda)

    /** Completes in the caller's thread, so a rejected completion rolls back the caller's transaction. */
    @Bean
    fun userTaskCompletionApi(camunda: CamundaClient, subscriptions: SubscriptionRepository): UserTaskCompletionApi =
        C8CamundaClientUserTaskCompletionApiImpl(camunda, subscriptions)

    @Bean
    fun taskSubscriptionApi(subscriptions: SubscriptionRepository): TaskSubscriptionApi =
        C8TaskSubscriptionApiImpl(subscriptions, null)

    /** Every worker names its retries and backoff, so the adapter's own retry counting stays unused. */
    @Bean
    fun serviceTaskCompletionApi(camunda: CamundaClient, subscriptions: SubscriptionRepository): ServiceTaskCompletionApi =
        C8ExternalServiceTaskCompletionApiImpl(camunda, subscriptions, object : FailureRetrySupplier {
            override fun apply(taskId: String) = FailureRetrySupplier.FailureRetry(retryCount = 0, retryTimeout = 0)
        })

    /** One job worker per subscription, with Camunda's default job timeout of five minutes. */
    @Bean
    fun serviceTaskDelivery(
        camunda: CamundaClient,
        subscriptions: SubscriptionRepository,
        @Value("\${spring.application.name}") workerName: String,
    ) = SubscribingServiceTaskDelivery(camunda, subscriptions, workerName, retryTimeoutInSeconds = 5, lockDurationInSeconds = 300)
}

/**
 * Opens the job workers whenever a Camunda client starts: once in production, and once per test under Camunda
 * Process Test, which gives every test a client of its own. The workers close with their client.
 */
@Component
@ConditionalOnProperty("camunda.client.worker.defaults.enabled", havingValue = "true", matchIfMissing = true)
class ServiceTaskWorkerLifecycle(private val delivery: SubscribingServiceTaskDelivery) : CamundaClientLifecycleAware {

    override fun onStart(client: CamundaClient) = delivery.subscribe()

    override fun onStop(client: CamundaClient) = Unit
}

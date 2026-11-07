package io.miragon.tasklist.order.engine.camunda

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientException
import io.camunda.client.api.command.ClientHttpException
import io.miragon.tasklist.order.engine.NoMessageSubscription
import io.miragon.tasklist.order.engine.ProcessEngineRefused
import java.util.concurrent.CompletableFuture

/**
 * Correlates a message to the process instance that waits for it. The C8 adapter 2026.09.1 publishes messages
 * instead, and Camunda keeps a published message for an hour when no instance waits yet. Correlating answers 404
 * right away, and the future fails with [NoMessageSubscription]. It runs in the caller's thread, so a
 * `@Transactional` caller rolls back.
 */
class C8CorrelationApi(private val camunda: CamundaClient) : CorrelationApi {

    override fun correlateMessage(cmd: CorrelateMessageCmd): CompletableFuture<Empty> {
        ensureSupported(cmd.restrictions)
        val correlationKey = cmd.correlation.get().correlationKey
        return try {
            camunda.newCorrelateMessageCommand()
                .messageName(cmd.messageName)
                .correlationKey(correlationKey)
                .variables(cmd.get())
                .send().join()
            CompletableFuture.completedFuture(Empty)
        } catch (e: ClientHttpException) {
            CompletableFuture.failedFuture(if (e.code() == 404) NoMessageSubscription(cmd.messageName, correlationKey) else ProcessEngineRefused(e))
        } catch (e: ClientException) {
            CompletableFuture.failedFuture(ProcessEngineRefused(e))
        }
    }

    override fun getSupportedRestrictions(): Set<String> = emptySet()

    override fun meta(instance: MetaInfoAware): MetaInfo = throw UnsupportedOperationException("The recipe reads no meta information.")
}

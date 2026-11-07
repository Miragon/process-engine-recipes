package io.miragon.tasklist.order.engine.camunda

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import dev.bpmcrafters.processengineapi.process.StartProcessCommand
import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ClientException
import io.miragon.tasklist.order.engine.ProcessEngineRefused
import java.util.concurrent.CompletableFuture

/**
 * Starts the latest version of a process like the C8 adapter does, and sets Camunda's business ID from the business key
 * in the payload, which the adapter 2026.09.1 drops. It runs in the caller's thread, so a `@Transactional` caller rolls
 * back when Camunda refuses the start.
 */
class C8StartProcessApi(private val camunda: CamundaClient) : StartProcessApi {

    override fun startProcess(cmd: StartProcessCommand): CompletableFuture<ProcessInformation> {
        require(cmd is StartProcessByDefinitionCmd) { "Only a start by definition is supported, not ${cmd.javaClass.simpleName}." }
        ensureSupported(cmd.restrictions)
        val payload = cmd.get()
        val businessKey = payload[CommonRestrictions.BUSINESS_KEY] as String?
        return try {
            val instance = camunda.newCreateInstanceCommand()
                .bpmnProcessId(cmd.definitionKey)
                .latestVersion()
                .variables(payload - CommonRestrictions.BUSINESS_KEY)
                .apply { if (businessKey != null) businessId(businessKey) }
                .send().join()
            CompletableFuture.completedFuture(
                ProcessInformation(
                    instance.processInstanceKey.toString(),
                    mapOf(
                        CommonRestrictions.PROCESS_DEFINITION_KEY to instance.bpmnProcessId,
                        CommonRestrictions.PROCESS_DEFINITION_ID to instance.processDefinitionKey.toString(),
                        CommonRestrictions.TENANT_ID to instance.tenantId,
                    ),
                ),
            )
        } catch (e: ClientException) {
            CompletableFuture.failedFuture(ProcessEngineRefused(e))
        }
    }

    override fun getSupportedRestrictions(): Set<String> = emptySet()

    override fun meta(instance: MetaInfoAware): MetaInfo = throw UnsupportedOperationException("The recipe reads no meta information.")
}

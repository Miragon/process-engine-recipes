package io.miragon.tasklist.taskmanager.projection

import io.camunda.client.CamundaClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.model.bpmn.instance.Process
import io.camunda.zeebe.model.bpmn.instance.UserTask
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Names and form references of user tasks, read once per process definition from its BPMN XML.
 * A deployed definition never changes, so the cache never expires.
 */
@Component
class ProcessDefinitions(private val camunda: CamundaClient) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<Long, Map<String, TaskDetails>>()

    /** Returns null while Camunda's search does not know the definition yet; the caller tries again later. */
    fun task(processDefinitionKey: Long, processDefinitionId: String, elementId: String): TaskDetails? =
        definition(processDefinitionKey, processDefinitionId)?.get(elementId)

    private fun definition(key: Long, processId: String): Map<String, TaskDetails>? =
        cache[key] ?: load(key, processId)?.also { cache[key] = it }

    private fun load(key: Long, processId: String): Map<String, TaskDetails>? =
        try {
            val xml = camunda.newProcessDefinitionGetXmlRequest(key)
                .requestTimeout(Duration.ofSeconds(2))
                .send()
                .join()
            parse(xml, processId)
        } catch (e: RuntimeException) {
            log.debug("BPMN XML of process definition {} not available yet: {}", key, e.message)
            null
        }

    private fun parse(xml: String, processId: String): Map<String, TaskDetails> {
        val model = Bpmn.readModelFromStream(xml.byteInputStream())
        val processName = model.getModelElementsByType(Process::class.java).firstOrNull { it.id == processId }?.name
        return model.getModelElementsByType(UserTask::class.java).associate { task ->
            // A FEEL expression is only known at runtime; the task's REST representation has the evaluated value.
            val formRef = task.getSingleExtensionElement(ZeebeFormDefinition::class.java)
                ?.externalReference
                ?.takeUnless { it.isBlank() || it.startsWith("=") }
            task.id to TaskDetails(task.name, processName, formRef)
        }
    }
}

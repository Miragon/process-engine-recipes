package io.miragon.tasklist.taskmanager.camunda

import io.camunda.client.CamundaClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.model.bpmn.instance.Process
import io.camunda.zeebe.model.bpmn.instance.UserTask
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition
import io.miragon.tasklist.taskmanager.projection.ProcessModels
import io.miragon.tasklist.taskmanager.projection.TaskDetails
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

/** [ProcessModels] from the BPMN XML in Camunda's search, which knows a definition shortly after its deployment. */
@Component
class CamundaProcessModels(private val camunda: CamundaClient) : ProcessModels {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun userTasks(processDefinitionKey: Long, processId: String): Map<String, TaskDetails>? =
        try {
            val xml = camunda.newProcessDefinitionGetXmlRequest(processDefinitionKey)
                .requestTimeout(Duration.ofSeconds(2))
                .send()
                .join()
            parse(xml, processId)
        } catch (e: RuntimeException) {
            log.debug("BPMN XML of process definition {} not available yet: {}", processDefinitionKey, e.message)
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

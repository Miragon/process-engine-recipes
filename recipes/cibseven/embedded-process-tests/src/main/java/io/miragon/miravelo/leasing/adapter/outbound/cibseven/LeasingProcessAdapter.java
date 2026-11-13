package io.miragon.miravelo.leasing.adapter.outbound.cibseven;

import io.miragon.miravelo.leasing.adapter.process.BikeLeasingProcessProcessApi;
import io.miragon.miravelo.leasing.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.miravelo.leasing.application.port.outbound.LeasingProcess;
import java.util.Map;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.task.Task;
import org.springframework.stereotype.Component;

@Component
public class LeasingProcessAdapter implements LeasingProcess {

    private static final String CONTRACT_VALUE = "contractValue";

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public LeasingProcessAdapter(RuntimeService runtimeService, TaskService taskService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @Override
    public void startLeasing(String applicationId, double contractValue) {
        runtimeService.startProcessInstanceByKey(
                BikeLeasingProcessProcessApi.PROCESS_ID.getValue(),
                applicationId,
                Map.of(CONTRACT_VALUE, contractValue));
    }

    @Override
    public void approveApplication(String applicationId) {
        completeTask(applicationId, FlowNodes.TaskApproveApplication.ELEMENT_ID);
    }

    @Override
    public void prepareContract(String applicationId) {
        completeTask(applicationId, FlowNodes.TaskPrepareContract.ELEMENT_ID);
    }

    private void completeTask(String applicationId, String taskDefinitionKey) {
        Task task = taskService.createTaskQuery()
                .processInstanceBusinessKey(applicationId)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
        if (task == null) {
            throw new IllegalStateException("No open '" + taskDefinitionKey + "' task for application " + applicationId);
        }
        taskService.complete(task.getId());
    }
}

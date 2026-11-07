package io.miragon.miravelo.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.cibseven.bpm.engine.FormService;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.form.FormField;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BikeRepairProcessTest {

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    TaskService taskService;

    @Autowired
    FormService formService;

    @Test
    void walksAllFiveFormTypes() throws IOException {
        ProcessInstance repair = runtimeService.startProcessInstanceByKey("bikeRepair");

        Task checkInBike = openTask(repair, "checkInBike");
        assertThat(formService.getTaskFormData(checkInBike.getId()).getFormFields())
                .extracting(FormField::getId)
                .containsExactly("frameNumber", "issueDescription");
        taskService.complete(checkInBike.getId(), Map.of("frameNumber", "WTU123", "issueDescription", "Rear brake rubs"));

        Task diagnoseBike = openTask(repair, "diagnoseBike");
        assertThat(diagnoseBike.getCamundaFormRef().getKey()).isEqualTo("diagnoseBike");
        assertThat(deployedForm(diagnoseBike)).contains("\"id\": \"diagnoseBike\"");
        taskService.complete(diagnoseBike.getId(), Map.of("findings", "Worn brake pads"));

        Task repairBike = openTask(repair, "repairBike");
        assertThat(repairBike.getFormKey()).isNull();
        assertThat(repairBike.getCamundaFormRef()).isNull();
        taskService.complete(repairBike.getId(), Map.of("workDone", "Replaced rear brake pads"));

        Task checkRepairQuality = openTask(repair, "checkRepairQuality");
        assertThat(checkRepairQuality.getFormKey()).isEqualTo("embedded:deployment:forms/check-repair-quality.html");
        assertThat(deployedForm(checkRepairQuality)).contains("cam-variable-name=\"brakesOk\"");
        taskService.complete(checkRepairQuality.getId(), Map.of("brakesOk", true, "gearsOk", true, "testRideOk", true));

        Task choosePickupSlot = openTask(repair, "choosePickupSlot");
        assertThat(choosePickupSlot.getFormKey()).isEqualTo("miravelo:choose-pickup-slot");
        assertThatThrownBy(() -> formService.getDeployedTaskForm(choosePickupSlot.getId()))
                .hasMessageContaining("does not reference a deployed form");
        taskService.complete(choosePickupSlot.getId(), Map.of("pickupSlot", "SATURDAY_MORNING"));

        assertThat(repair).isEnded();
    }

    private Task openTask(ProcessInstance repair, String taskDefinitionKey) {
        return taskService.createTaskQuery()
                .processInstanceId(repair.getId())
                .taskDefinitionKey(taskDefinitionKey)
                .initializeFormKeys()
                .singleResult();
    }

    private String deployedForm(Task task) throws IOException {
        try (InputStream form = formService.getDeployedTaskForm(task.getId())) {
            return new String(form.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

package io.miragon.miravelo.leasing.adapter.inbound.cibseven.history;

import static io.miragon.miravelo.leasing.adapter.inbound.cibseven.history.PublishedProcessEvents.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import io.miragon.miravelo.leasing.adapter.process.BikeLeasingProcessProcessApi;
import io.miragon.miravelo.leasing.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.miravelo.leasing.application.port.inbound.OrderBikeUseCase;
import org.cibseven.bpm.engine.HistoryService;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestChannelBinderConfiguration.class)
class ProcessEventsProcessTest {

    private static final String APPLICATION_ID = "APP-4711";

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private OutputDestination output;

    @MockitoBean
    private OrderBikeUseCase orderBike;

    @BeforeEach
    void clearDestination() {
        output.clear();
    }

    @Test
    void publishesEveryProcessEventOfALeasingApplication() {
        ProcessInstance leasing = startLeasing();

        approveApplication(leasing);

        assertThat(PublishedProcessEvents.receiveAll(output)).containsExactly(
                event("PROCESS_STARTED", null, APPLICATION_ID),
                event("TASK_COMPLETED", FlowNodes.TaskApproveApplication.ELEMENT_ID, APPLICATION_ID),
                event("TASK_COMPLETED", FlowNodes.TaskOrderBike.ELEMENT_ID, APPLICATION_ID),
                event("PROCESS_ENDED", null, APPLICATION_ID));
    }

    @Test
    void keepsTheEngineHistoryForCockpit() {
        ProcessInstance leasing = startLeasing();

        approveApplication(leasing);

        assertThat(historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(leasing.getId())
                .activityId(FlowNodes.TaskApproveApplication.ELEMENT_ID)
                .finished()
                .count()).isEqualTo(1);
    }

    @Test
    void publishesNothingWhenTheTransactionRollsBack() {
        ProcessInstance leasing = startLeasing();
        doThrow(new IllegalStateException("Supplier unreachable")).when(orderBike).orderBike(anyString());
        output.clear();

        assertThatThrownBy(() -> approveApplication(leasing)).hasMessageContaining("Supplier unreachable");

        assertThat(PublishedProcessEvents.receiveAll(output)).isEmpty();
        assertThat(openApprovalTasks(leasing)).isEqualTo(1);
    }

    private ProcessInstance startLeasing() {
        return runtimeService.startProcessInstanceByKey(BikeLeasingProcessProcessApi.PROCESS_ID.getValue(), APPLICATION_ID);
    }

    private void approveApplication(ProcessInstance leasing) {
        String taskId = taskService.createTaskQuery()
                .processInstanceId(leasing.getId())
                .taskDefinitionKey(FlowNodes.TaskApproveApplication.ELEMENT_ID)
                .singleResult()
                .getId();
        taskService.complete(taskId);
    }

    private long openApprovalTasks(ProcessInstance leasing) {
        return taskService.createTaskQuery()
                .processInstanceId(leasing.getId())
                .taskDefinitionKey(FlowNodes.TaskApproveApplication.ELEMENT_ID)
                .count();
    }
}

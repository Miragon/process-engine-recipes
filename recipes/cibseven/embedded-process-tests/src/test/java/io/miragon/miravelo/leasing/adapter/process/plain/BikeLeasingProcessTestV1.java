package io.miragon.miravelo.leasing.adapter.process.plain;

import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.processInstanceQuery;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.miragon.miravelo.leasing.application.port.inbound.AutoApproveApplicationUseCase;
import io.miragon.miravelo.leasing.application.port.inbound.HandOverBikeUseCase;
import io.miragon.miravelo.leasing.application.port.inbound.OrderBikeUseCase;
import io.miragon.miravelo.leasing.application.port.inbound.SendApprovalReminderUseCase;
import io.miragon.miravelo.leasing.application.port.outbound.LeasingProcess;
import java.util.UUID;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class BikeLeasingProcessTestV1 {

    private final String applicationId = UUID.randomUUID().toString();

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private LeasingProcess leasingProcess;

    @MockitoBean
    private AutoApproveApplicationUseCase autoApproveApplicationUseCase;

    @MockitoBean
    private SendApprovalReminderUseCase sendApprovalReminderUseCase;

    @MockitoBean
    private OrderBikeUseCase orderBikeUseCase;

    @MockitoBean
    private HandOverBikeUseCase handOverBikeUseCase;

    @BeforeEach
    void setUp() {
        init(processEngine);
        when(orderBikeUseCase.orderBike(applicationId)).thenReturn("ORDER-1");
    }

    @Test
    void lowContractValueIsApprovedAutomatically() {
        ProcessInstance leasing = startLeasing(500);

        execute(job("Task_AutoApprove", leasing));
        leasingProcess.prepareContract(applicationId);
        execute(job("Task_OrderBike", leasing));
        execute(job("Task_HandOver", leasing));

        assertThat(leasing).isEnded()
                .hasPassedInOrder(
                        "StartEvent_leasingRequestReceived",
                        "Gateway_contractValue",
                        "Task_AutoApprove",
                        "Gateway_approvalMerge",
                        "Task_PrepareContract",
                        "Task_OrderBike",
                        "Task_HandOver",
                        "EndEvent_leasingActive")
                .hasNotPassed("Task_ApproveApplication");
        verify(autoApproveApplicationUseCase).autoApprove(applicationId);
        verify(handOverBikeUseCase).handOver(applicationId, "ORDER-1");
    }

    @Test
    void highContractValueWaitsForTheManager() {
        ProcessInstance leasing = startLeasing(5000);

        assertThat(leasing).isWaitingAt("Task_ApproveApplication");

        leasingProcess.approveApplication(applicationId);
        leasingProcess.prepareContract(applicationId);
        execute(job("Task_OrderBike", leasing));
        execute(job("Task_HandOver", leasing));

        assertThat(leasing).isEnded()
                .hasPassedInOrder(
                        "StartEvent_leasingRequestReceived",
                        "Gateway_contractValue",
                        "Task_ApproveApplication",
                        "Gateway_approvalMerge",
                        "Task_PrepareContract",
                        "Task_OrderBike",
                        "Task_HandOver",
                        "EndEvent_leasingActive")
                .hasNotPassed("Task_AutoApprove", "Task_SendReminder");
        verifyNoInteractions(autoApproveApplicationUseCase);
        verify(handOverBikeUseCase).handOver(applicationId, "ORDER-1");
    }

    @Test
    void silentManagerIsRemindedAfterThreeDays() {
        ProcessInstance leasing = startLeasing(5000);

        execute(job("BoundaryEvent_reminder", leasing));
        execute(job("Task_SendReminder", leasing));

        assertThat(leasing)
                .hasPassedInOrder("BoundaryEvent_reminder", "Task_SendReminder", "EndEvent_reminderSent")
                .isWaitingAt("Task_ApproveApplication");
        verify(sendApprovalReminderUseCase).sendReminder(applicationId);
    }

    private ProcessInstance startLeasing(double contractValue) {
        leasingProcess.startLeasing(applicationId, contractValue);
        return processInstanceQuery().processInstanceBusinessKey(applicationId).singleResult();
    }
}

package io.miragon.miravelo.leasing.adapter.process.plain;

import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.processInstanceQuery;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.miragon.bpmn.runtime.path.PathWalk;
import io.miragon.miravelo.leasing.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
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
class BikeLeasingProcessTestV2 {

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

        execute(job(FlowNodes.TaskAutoApprove.ELEMENT_ID, leasing));
        leasingProcess.prepareContract(applicationId);
        execute(job(FlowNodes.TaskOrderBike.ELEMENT_ID, leasing));
        execute(job(FlowNodes.TaskHandOver.ELEMENT_ID, leasing));

        assertThat(leasing).isEnded()
                .hasPassedInOrder(PathWalk.from(FlowNodes.startEventLeasingRequestReceived())
                        .then(next -> next.gatewayContractValue())
                        .then(next -> next.taskAutoApprove())
                        .then(next -> next.gatewayApprovalMerge())
                        .then(next -> next.taskPrepareContract())
                        .then(next -> next.taskOrderBike())
                        .then(next -> next.taskHandOver())
                        .end(next -> next.endEventLeasingActive())
                        .getIds())
                .hasNotPassed(FlowNodes.TaskApproveApplication.ELEMENT_ID);
        verify(autoApproveApplicationUseCase).autoApprove(applicationId);
        verify(handOverBikeUseCase).handOver(applicationId, "ORDER-1");
    }

    @Test
    void highContractValueWaitsForTheManager() {
        ProcessInstance leasing = startLeasing(5000);

        assertThat(leasing).isWaitingAt(FlowNodes.TaskApproveApplication.ELEMENT_ID);

        leasingProcess.approveApplication(applicationId);
        leasingProcess.prepareContract(applicationId);
        execute(job(FlowNodes.TaskOrderBike.ELEMENT_ID, leasing));
        execute(job(FlowNodes.TaskHandOver.ELEMENT_ID, leasing));

        assertThat(leasing).isEnded()
                .hasPassedInOrder(PathWalk.from(FlowNodes.startEventLeasingRequestReceived())
                        .then(next -> next.gatewayContractValue())
                        .then(next -> next.taskApproveApplication())
                        .then(next -> next.gatewayApprovalMerge())
                        .then(next -> next.taskPrepareContract())
                        .then(next -> next.taskOrderBike())
                        .then(next -> next.taskHandOver())
                        .end(next -> next.endEventLeasingActive())
                        .getIds())
                .hasNotPassed(FlowNodes.TaskAutoApprove.ELEMENT_ID, FlowNodes.TaskSendReminder.ELEMENT_ID);
        verifyNoInteractions(autoApproveApplicationUseCase);
        verify(handOverBikeUseCase).handOver(applicationId, "ORDER-1");
    }

    @Test
    void silentManagerIsRemindedAfterThreeDays() {
        ProcessInstance leasing = startLeasing(5000);

        execute(job(FlowNodes.BoundaryEventReminder.ELEMENT_ID, leasing));
        execute(job(FlowNodes.TaskSendReminder.ELEMENT_ID, leasing));

        assertThat(leasing)
                .hasPassedInOrder(PathWalk.from(FlowNodes.boundaryEventReminder())
                        .then(next -> next.taskSendReminder())
                        .end(next -> next.endEventReminderSent())
                        .getIds())
                .isWaitingAt(FlowNodes.TaskApproveApplication.ELEMENT_ID);
        verify(sendApprovalReminderUseCase).sendReminder(applicationId);
    }

    private ProcessInstance startLeasing(double contractValue) {
        leasingProcess.startLeasing(applicationId, contractValue);
        return processInstanceQuery().processInstanceBusinessKey(applicationId).singleResult();
    }
}

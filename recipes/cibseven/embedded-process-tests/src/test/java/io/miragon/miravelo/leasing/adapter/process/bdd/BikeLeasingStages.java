package io.miragon.miravelo.leasing.adapter.process.bdd;

import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tngtech.jgiven.annotation.BeforeStage;
import com.tngtech.jgiven.annotation.ExpectedScenarioState;
import com.tngtech.jgiven.annotation.ProvidedScenarioState;
import com.tngtech.jgiven.annotation.Quoted;
import io.miragon.miravelo.leasing.adapter.inbound.cibseven.AutoApproveDelegate;
import io.miragon.miravelo.leasing.adapter.inbound.cibseven.HandOverBikeDelegate;
import io.miragon.miravelo.leasing.adapter.inbound.cibseven.OrderBikeDelegate;
import io.miragon.miravelo.leasing.adapter.inbound.cibseven.SendReminderDelegate;
import io.miragon.miravelo.leasing.adapter.outbound.cibseven.LeasingProcessAdapter;
import io.miragon.miravelo.leasing.application.port.inbound.AutoApproveApplicationUseCase;
import io.miragon.miravelo.leasing.application.port.inbound.HandOverBikeUseCase;
import io.miragon.miravelo.leasing.application.port.inbound.OrderBikeUseCase;
import io.miragon.miravelo.leasing.application.port.inbound.SendApprovalReminderUseCase;
import io.miragon.miravelo.leasing.application.port.outbound.LeasingProcess;
import java.util.UUID;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.test.mock.Mocks;
import org.cibseven.community.bpm.extension.jgiven.ProcessStage;
import org.cibseven.community.bpm.extension.jgiven.SelfUpdatingInstanceSupplier;
import org.mockito.Mockito;

final class BikeLeasingStages {

    private BikeLeasingStages() {
    }

    public static class Action extends ProcessStage<Action, SelfUpdatingInstanceSupplier> {

        @ProvidedScenarioState
        String applicationId = UUID.randomUUID().toString();

        @ProvidedScenarioState
        AutoApproveApplicationUseCase autoApproveApplicationUseCase = mock(AutoApproveApplicationUseCase.class);

        @ProvidedScenarioState
        SendApprovalReminderUseCase sendApprovalReminderUseCase = mock(SendApprovalReminderUseCase.class);

        @ProvidedScenarioState
        OrderBikeUseCase orderBikeUseCase = mock(OrderBikeUseCase.class);

        @ProvidedScenarioState
        HandOverBikeUseCase handOverBikeUseCase = mock(HandOverBikeUseCase.class);

        private LeasingProcess leasingProcess;

        private double contractValue;

        @BeforeStage
        void wireDelegatesAndProcessAdapter() {
            init(camunda);
            Mocks.register("autoApproveDelegate", new AutoApproveDelegate(autoApproveApplicationUseCase));
            Mocks.register("sendReminderDelegate", new SendReminderDelegate(sendApprovalReminderUseCase));
            Mocks.register("orderBikeDelegate", new OrderBikeDelegate(orderBikeUseCase));
            Mocks.register("handOverBikeDelegate", new HandOverBikeDelegate(handOverBikeUseCase));
            leasingProcess = new LeasingProcessAdapter(camunda.getRuntimeService(), camunda.getTaskService());
        }

        public Action a_leasing_application_worth(double contractValue) {
            this.contractValue = contractValue;
            return self();
        }

        public Action the_dealer_confirms_bike_orders_as(@Quoted String orderId) {
            Mockito.when(orderBikeUseCase.orderBike(applicationId)).thenReturn(orderId);
            return self();
        }

        public Action the_application_is_submitted() {
            leasingProcess.startLeasing(applicationId, contractValue);
            ProcessInstance leasing = camunda.getRuntimeService().createProcessInstanceQuery()
                    .processInstanceBusinessKey(applicationId)
                    .singleResult();
            processInstanceSupplier = new SelfUpdatingInstanceSupplier(
                    leasing.getId(), camunda.getRuntimeService(), leasing);
            return self();
        }

        public Action the_application_is_approved_automatically() {
            runJobOf("Task_AutoApprove");
            return self();
        }

        public Action the_manager_approves_the_application() {
            leasingProcess.approveApplication(applicationId);
            return self();
        }

        public Action three_days_pass_without_approval() {
            runJobOf("BoundaryEvent_reminder");
            return self();
        }

        public Action the_reminder_is_sent() {
            runJobOf("Task_SendReminder");
            return self();
        }

        public Action the_contract_is_prepared() {
            leasingProcess.prepareContract(applicationId);
            return self();
        }

        public Action the_bike_is_ordered() {
            runJobOf("Task_OrderBike");
            return self();
        }

        public Action the_bike_is_handed_over() {
            runJobOf("Task_HandOver");
            return self();
        }

        private void runJobOf(String activityId) {
            execute(job(activityId, processInstanceSupplier.get()));
        }
    }

    public static class Then extends ProcessStage<Then, SelfUpdatingInstanceSupplier> {

        @ExpectedScenarioState
        String applicationId;

        @ExpectedScenarioState
        AutoApproveApplicationUseCase autoApproveApplicationUseCase;

        @ExpectedScenarioState
        SendApprovalReminderUseCase sendApprovalReminderUseCase;

        @ExpectedScenarioState
        HandOverBikeUseCase handOverBikeUseCase;

        public Then the_application_was_approved_automatically() {
            verify(autoApproveApplicationUseCase).autoApprove(applicationId);
            return self();
        }

        public Then no_automatic_approval_happened() {
            verifyNoInteractions(autoApproveApplicationUseCase);
            return self();
        }

        public Then the_manager_was_reminded() {
            verify(sendApprovalReminderUseCase).sendReminder(applicationId);
            return self();
        }

        public Then the_bike_of_order_$_was_handed_over(@Quoted String orderId) {
            verify(handOverBikeUseCase).handOver(applicationId, orderId);
            return self();
        }
    }
}

package io.miragon.miravelo.leasing.adapter.process.bdd;

import com.tngtech.jgiven.annotation.ScenarioState;
import com.tngtech.jgiven.junit5.ScenarioTest;
import io.miragon.bpmn.runtime.path.PathWalk;
import io.miragon.miravelo.leasing.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.test.Deployment;
import org.cibseven.bpm.engine.test.junit5.ProcessEngineExtension;
import org.cibseven.bpm.engine.test.mock.Mocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@Deployment(resources = "bpmn/bike-leasing.bpmn")
class BikeLeasingProcessTestV2
        extends ScenarioTest<BikeLeasingStages.Action, BikeLeasingStages.Action, BikeLeasingStages.Then> {

    @RegisterExtension
    static ProcessEngineExtension engineExtension = InMemoryProcessEngine.withCoverage();

    @ScenarioState
    ProcessEngine processEngine = engineExtension.getProcessEngine();

    @AfterEach
    void forgetDelegates() {
        Mocks.reset();
    }

    @Test
    void low_contract_value_is_approved_automatically() {
        given().a_leasing_application_worth(500)
                .and().the_dealer_confirms_bike_orders_as("ORDER-1");

        when().the_application_is_submitted()
                .and().the_application_is_approved_automatically()
                .and().the_contract_is_prepared()
                .and().the_bike_is_ordered()
                .and().the_bike_is_handed_over();

        then().process_is_finished(FlowNodes.EndEventLeasingActive.ELEMENT_ID)
                .and().process_has_passed(PathWalk.from(FlowNodes.startEventLeasingRequestReceived())
                        .then(next -> next.gatewayContractValue())
                        .then(next -> next.taskAutoApprove())
                        .then(next -> next.gatewayApprovalMerge())
                        .then(next -> next.taskPrepareContract())
                        .then(next -> next.taskOrderBike())
                        .then(next -> next.taskHandOver())
                        .end(next -> next.endEventLeasingActive())
                        .getIds())
                .and().process_has_not_passed(FlowNodes.TaskApproveApplication.ELEMENT_ID)
                .and().the_application_was_approved_automatically()
                .and().the_bike_of_order_$_was_handed_over("ORDER-1");
    }

    @Test
    void high_contract_value_waits_for_the_manager() {
        given().a_leasing_application_worth(5000)
                .and().the_dealer_confirms_bike_orders_as("ORDER-1");

        when().the_application_is_submitted();

        then().process_waits_in(FlowNodes.TaskApproveApplication.ELEMENT_ID);

        when().the_manager_approves_the_application()
                .and().the_contract_is_prepared()
                .and().the_bike_is_ordered()
                .and().the_bike_is_handed_over();

        then().process_is_finished(FlowNodes.EndEventLeasingActive.ELEMENT_ID)
                .and().process_has_passed(PathWalk.from(FlowNodes.startEventLeasingRequestReceived())
                        .then(next -> next.gatewayContractValue())
                        .then(next -> next.taskApproveApplication())
                        .then(next -> next.gatewayApprovalMerge())
                        .then(next -> next.taskPrepareContract())
                        .then(next -> next.taskOrderBike())
                        .then(next -> next.taskHandOver())
                        .end(next -> next.endEventLeasingActive())
                        .getIds())
                .and().process_has_not_passed(
                        FlowNodes.TaskAutoApprove.ELEMENT_ID, FlowNodes.TaskSendReminder.ELEMENT_ID)
                .and().no_automatic_approval_happened()
                .and().the_bike_of_order_$_was_handed_over("ORDER-1");
    }

    @Test
    void silent_manager_is_reminded_after_three_days() {
        given().a_leasing_application_worth(5000);

        when().the_application_is_submitted()
                .and().three_days_pass_without_approval()
                .and().the_reminder_is_sent();

        then().process_has_passed(PathWalk.from(FlowNodes.boundaryEventReminder())
                        .then(next -> next.taskSendReminder())
                        .end(next -> next.endEventReminderSent())
                        .getIds())
                .and().process_waits_in(FlowNodes.TaskApproveApplication.ELEMENT_ID)
                .and().the_manager_was_reminded();
    }
}

package io.miragon.miravelo.leasing.adapter.inbound.cibseven.history;

import io.miragon.miravelo.leasing.application.domain.ProcessMilestone;
import io.miragon.miravelo.leasing.application.domain.ProcessMilestone.Type;
import io.miragon.miravelo.leasing.application.port.inbound.PublishProcessMilestoneUseCase;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cibseven.bpm.engine.ActivityTypes;
import org.cibseven.bpm.engine.impl.cfg.TransactionState;
import org.cibseven.bpm.engine.impl.context.Context;
import org.cibseven.bpm.engine.impl.history.event.HistoricActivityInstanceEventEntity;
import org.cibseven.bpm.engine.impl.history.event.HistoricProcessInstanceEventEntity;
import org.cibseven.bpm.engine.impl.history.event.HistoricScopeInstanceEvent;
import org.cibseven.bpm.engine.impl.history.event.HistoryEvent;
import org.cibseven.bpm.engine.impl.history.event.HistoryEventTypes;
import org.cibseven.bpm.engine.impl.history.handler.HistoryEventHandler;

class ProcessMilestoneHistoryEventHandler implements HistoryEventHandler {

    private static final Set<String> MILESTONE_ACTIVITY_TYPES = Set.of(ActivityTypes.TASK_USER_TASK, ActivityTypes.TASK_SERVICE);

    private final PublishProcessMilestoneUseCase useCase;

    ProcessMilestoneHistoryEventHandler(PublishProcessMilestoneUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void handleEvent(HistoryEvent event) {
        toMilestone(event).ifPresent(this::publishAfterCommit);
    }

    @Override
    public void handleEvents(List<HistoryEvent> events) {
        events.forEach(this::handleEvent);
    }

    private Optional<ProcessMilestone> toMilestone(HistoryEvent event) {
        if (event instanceof HistoricProcessInstanceEventEntity) {
            return processMilestone(event);
        }
        if (event instanceof HistoricActivityInstanceEventEntity activity) {
            return taskMilestone(activity);
        }
        return Optional.empty();
    }

    private Optional<ProcessMilestone> processMilestone(HistoryEvent event) {
        if (event.isEventOfType(HistoryEventTypes.PROCESS_INSTANCE_START)) {
            return Optional.of(milestone(event, Type.PROCESS_STARTED, null));
        }
        if (event.isEventOfType(HistoryEventTypes.PROCESS_INSTANCE_END)) {
            return Optional.of(milestone(event, Type.PROCESS_ENDED, null));
        }
        return Optional.empty();
    }

    private Optional<ProcessMilestone> taskMilestone(HistoricActivityInstanceEventEntity activity) {
        if (activity.isEventOfType(HistoryEventTypes.ACTIVITY_INSTANCE_END)
                && MILESTONE_ACTIVITY_TYPES.contains(activity.getActivityType())) {
            return Optional.of(milestone(activity, Type.TASK_COMPLETED, activity.getActivityId()));
        }
        return Optional.empty();
    }

    private ProcessMilestone milestone(HistoryEvent event, Type type, String activityId) {
        HistoricScopeInstanceEvent scope = (HistoricScopeInstanceEvent) event;
        return new ProcessMilestone(
                type,
                event.getProcessDefinitionKey(),
                event.getProcessInstanceId(),
                businessKey(event),
                activityId,
                (type == Type.PROCESS_STARTED ? scope.getStartTime() : scope.getEndTime()).toInstant());
    }

    private String businessKey(HistoryEvent event) {
        if (event instanceof HistoricProcessInstanceEventEntity processInstance) {
            return processInstance.getBusinessKey();
        }
        return Context.getCommandContext()
                .getExecutionManager()
                .findExecutionById(event.getProcessInstanceId())
                .getBusinessKey();
    }

    private void publishAfterCommit(ProcessMilestone milestone) {
        Context.getCommandContext()
                .getTransactionContext()
                .addTransactionListener(TransactionState.COMMITTED, committed -> useCase.publish(milestone));
    }
}

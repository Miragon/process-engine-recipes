package io.miragon.miravelo.leasing.adapter.inbound.cibseven.history;

import io.miragon.miravelo.leasing.application.domain.ProcessEvent;
import io.miragon.miravelo.leasing.application.domain.ProcessEvent.Type;
import io.miragon.miravelo.leasing.application.port.inbound.PublishProcessEventUseCase;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cibseven.bpm.engine.ActivityTypes;
import org.cibseven.bpm.engine.impl.cfg.TransactionState;
import org.cibseven.bpm.engine.impl.context.Context;
import org.cibseven.bpm.engine.impl.history.event.HistoricActivityInstanceEventEntity;
import org.cibseven.bpm.engine.impl.history.event.HistoricProcessInstanceEventEntity;
import org.cibseven.bpm.engine.impl.history.event.HistoryEvent;
import org.cibseven.bpm.engine.impl.history.event.HistoryEventTypes;
import org.cibseven.bpm.engine.impl.history.handler.HistoryEventHandler;

class ProcessEventsHistoryHandler implements HistoryEventHandler {

    private static final Set<String> TASK_TYPES = Set.of(ActivityTypes.TASK_USER_TASK, ActivityTypes.TASK_SERVICE);

    private final PublishProcessEventUseCase useCase;

    ProcessEventsHistoryHandler(PublishProcessEventUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void handleEvent(HistoryEvent historyEvent) {
        toProcessEvent(historyEvent).ifPresent(this::publishAfterCommit);
    }

    @Override
    public void handleEvents(List<HistoryEvent> historyEvents) {
        historyEvents.forEach(this::handleEvent);
    }

    private Optional<ProcessEvent> toProcessEvent(HistoryEvent historyEvent) {
        return switch (historyEvent) {
            case HistoricProcessInstanceEventEntity processInstance -> fromProcessInstance(processInstance);
            case HistoricActivityInstanceEventEntity activity -> fromActivity(activity);
            default -> Optional.empty();
        };
    }

    private Optional<ProcessEvent> fromProcessInstance(HistoricProcessInstanceEventEntity processInstance) {
        String businessKey = processInstance.getBusinessKey();
        if (processInstance.isEventOfType(HistoryEventTypes.PROCESS_INSTANCE_START)) {
            return Optional.of(processEvent(Type.PROCESS_STARTED, processInstance, businessKey, null, processInstance.getStartTime()));
        } else if (processInstance.isEventOfType(HistoryEventTypes.PROCESS_INSTANCE_END)) {
            return Optional.of(processEvent(Type.PROCESS_ENDED, processInstance, businessKey, null, processInstance.getEndTime()));
        } else {
            return Optional.empty();
        }
    }

    private Optional<ProcessEvent> fromActivity(HistoricActivityInstanceEventEntity activity) {
        boolean taskCompleted = activity.isEventOfType(HistoryEventTypes.ACTIVITY_INSTANCE_END)
                && TASK_TYPES.contains(activity.getActivityType());
        if (taskCompleted) {
            return Optional.of(processEvent(Type.TASK_COMPLETED, activity, businessKeyOf(activity), activity.getActivityId(), activity.getEndTime()));
        } else {
            return Optional.empty();
        }
    }

    private ProcessEvent processEvent(Type type, HistoryEvent historyEvent, String businessKey, String activityId, Date occurredAt) {
        return new ProcessEvent(
                type,
                historyEvent.getProcessDefinitionKey(),
                historyEvent.getProcessInstanceId(),
                businessKey,
                activityId,
                occurredAt.toInstant());
    }

    private String businessKeyOf(HistoricActivityInstanceEventEntity activity) {
        return Context.getCommandContext()
                .getExecutionManager()
                .findExecutionById(activity.getProcessInstanceId())
                .getBusinessKey();
    }

    private void publishAfterCommit(ProcessEvent event) {
        Context.getCommandContext()
                .getTransactionContext()
                .addTransactionListener(TransactionState.COMMITTED, committed -> useCase.publish(event));
    }
}

package io.miragon.miravelo.leasing.application.domain;

import java.time.Instant;

public record ProcessMilestone(
        Type type,
        String processDefinitionKey,
        String processInstanceId,
        String businessKey,
        String activityId,
        Instant occurredAt) {

    public enum Type {
        PROCESS_STARTED,
        TASK_COMPLETED,
        PROCESS_ENDED
    }
}

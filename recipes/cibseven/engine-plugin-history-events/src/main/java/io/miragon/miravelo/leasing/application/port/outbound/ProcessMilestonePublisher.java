package io.miragon.miravelo.leasing.application.port.outbound;

import io.miragon.miravelo.leasing.application.domain.ProcessMilestone;

public interface ProcessMilestonePublisher {

    void publish(ProcessMilestone milestone);
}

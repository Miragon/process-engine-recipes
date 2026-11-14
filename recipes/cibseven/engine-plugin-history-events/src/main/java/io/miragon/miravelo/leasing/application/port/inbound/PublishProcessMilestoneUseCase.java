package io.miragon.miravelo.leasing.application.port.inbound;

import io.miragon.miravelo.leasing.application.domain.ProcessMilestone;

public interface PublishProcessMilestoneUseCase {

    void publish(ProcessMilestone milestone);
}

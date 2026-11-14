package io.miragon.miravelo.leasing.application.service;

import io.miragon.miravelo.leasing.application.domain.ProcessMilestone;
import io.miragon.miravelo.leasing.application.port.inbound.PublishProcessMilestoneUseCase;
import io.miragon.miravelo.leasing.application.port.outbound.ProcessMilestonePublisher;
import org.springframework.stereotype.Service;

@Service
public class PublishProcessMilestoneService implements PublishProcessMilestoneUseCase {

    private final ProcessMilestonePublisher publisher;

    public PublishProcessMilestoneService(ProcessMilestonePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(ProcessMilestone milestone) {
        publisher.publish(milestone);
    }
}

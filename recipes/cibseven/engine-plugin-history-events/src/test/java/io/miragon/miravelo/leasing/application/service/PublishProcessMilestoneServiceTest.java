package io.miragon.miravelo.leasing.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.miravelo.leasing.application.domain.ProcessMilestone;
import io.miragon.miravelo.leasing.application.domain.ProcessMilestone.Type;
import io.miragon.miravelo.leasing.application.port.outbound.ProcessMilestonePublisher;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PublishProcessMilestoneServiceTest {

    private final ProcessMilestonePublisher publisher = mock(ProcessMilestonePublisher.class);
    private final PublishProcessMilestoneService underTest = new PublishProcessMilestoneService(publisher);

    @Test
    void handsTheMilestoneToThePublisher() {
        ProcessMilestone milestone = new ProcessMilestone(
                Type.TASK_COMPLETED, "bikeLeasingProcess", "instance-1", "APP-4711", "Task_ApproveApplication", Instant.now());

        underTest.publish(milestone);

        verify(publisher).publish(milestone);
    }
}

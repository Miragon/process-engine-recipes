package io.miragon.miravelo.leasing.adapter.outbound.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.miravelo.leasing.application.domain.ProcessMilestone;
import io.miragon.miravelo.leasing.application.domain.ProcessMilestone.Type;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;

class StreamProcessMilestonePublisherTest {

    private final StreamBridge streamBridge = mock(StreamBridge.class);
    private final StreamProcessMilestonePublisher underTest = new StreamProcessMilestonePublisher(streamBridge);

    @Test
    void sendsTheMilestoneToItsBinding() {
        ProcessMilestone milestone = new ProcessMilestone(
                Type.PROCESS_STARTED, "bikeLeasingProcess", "instance-1", "APP-4711", null, Instant.now());

        underTest.publish(milestone);

        verify(streamBridge).send("processMilestones-out-0", milestone);
    }
}

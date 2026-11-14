package io.miragon.miravelo.leasing.adapter.outbound.stream;

import io.miragon.miravelo.leasing.application.domain.ProcessMilestone;
import io.miragon.miravelo.leasing.application.port.outbound.ProcessMilestonePublisher;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
public class StreamProcessMilestonePublisher implements ProcessMilestonePublisher {

    static final String BINDING = "processMilestones-out-0";

    private final StreamBridge streamBridge;

    public StreamProcessMilestonePublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publish(ProcessMilestone milestone) {
        streamBridge.send(BINDING, milestone);
    }
}

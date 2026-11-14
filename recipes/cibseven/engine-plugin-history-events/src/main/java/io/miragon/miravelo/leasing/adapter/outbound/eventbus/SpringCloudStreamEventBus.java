package io.miragon.miravelo.leasing.adapter.outbound.eventbus;

import io.miragon.miravelo.leasing.application.domain.ProcessEvent;
import io.miragon.miravelo.leasing.application.port.outbound.EventBus;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
public class SpringCloudStreamEventBus implements EventBus {

    static final String BINDING = "processEvents-out-0";

    private final StreamBridge streamBridge;

    public SpringCloudStreamEventBus(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public void publish(ProcessEvent event) {
        streamBridge.send(BINDING, event);
    }
}

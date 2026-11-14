package io.miragon.miravelo.leasing.adapter.outbound.eventbus;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.miravelo.leasing.application.domain.ProcessEvent;
import io.miragon.miravelo.leasing.application.domain.ProcessEvent.Type;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;

class SpringCloudStreamEventBusTest {

    private final StreamBridge streamBridge = mock(StreamBridge.class);
    private final SpringCloudStreamEventBus underTest = new SpringCloudStreamEventBus(streamBridge);

    @Test
    void sendsTheEventToItsBinding() {
        ProcessEvent event = new ProcessEvent(
                Type.PROCESS_STARTED, "bikeLeasingProcess", "instance-1", "APP-4711", null, Instant.now());

        underTest.publish(event);

        verify(streamBridge).send("processEvents-out-0", event);
    }
}

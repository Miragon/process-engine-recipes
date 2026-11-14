package io.miragon.miravelo.leasing.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.miravelo.leasing.application.domain.ProcessEvent;
import io.miragon.miravelo.leasing.application.domain.ProcessEvent.Type;
import io.miragon.miravelo.leasing.application.port.outbound.EventBus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PublishProcessEventServiceTest {

    private final EventBus eventBus = mock(EventBus.class);
    private final PublishProcessEventService underTest = new PublishProcessEventService(eventBus);

    @Test
    void handsTheEventToTheEventBus() {
        ProcessEvent event = new ProcessEvent(
                Type.TASK_COMPLETED, "bikeLeasingProcess", "instance-1", "APP-4711", "Task_ApproveApplication", Instant.now());

        underTest.publish(event);

        verify(eventBus).publish(event);
    }
}

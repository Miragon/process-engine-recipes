package io.miragon.miravelo.leasing.application.service;

import io.miragon.miravelo.leasing.application.domain.ProcessEvent;
import io.miragon.miravelo.leasing.application.port.inbound.PublishProcessEventUseCase;
import io.miragon.miravelo.leasing.application.port.outbound.EventBus;
import org.springframework.stereotype.Service;

@Service
public class PublishProcessEventService implements PublishProcessEventUseCase {

    private final EventBus eventBus;

    public PublishProcessEventService(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void publish(ProcessEvent event) {
        eventBus.publish(event);
    }
}

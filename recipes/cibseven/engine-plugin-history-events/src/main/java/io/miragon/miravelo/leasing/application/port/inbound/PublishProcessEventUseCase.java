package io.miragon.miravelo.leasing.application.port.inbound;

import io.miragon.miravelo.leasing.application.domain.ProcessEvent;

public interface PublishProcessEventUseCase {

    void publish(ProcessEvent event);
}

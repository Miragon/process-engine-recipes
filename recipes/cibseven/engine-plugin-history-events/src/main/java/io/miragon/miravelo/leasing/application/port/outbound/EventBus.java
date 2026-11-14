package io.miragon.miravelo.leasing.application.port.outbound;

import io.miragon.miravelo.leasing.application.domain.ProcessEvent;

public interface EventBus {

    void publish(ProcessEvent event);
}

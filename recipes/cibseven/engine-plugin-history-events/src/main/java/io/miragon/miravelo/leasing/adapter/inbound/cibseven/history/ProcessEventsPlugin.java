package io.miragon.miravelo.leasing.adapter.inbound.cibseven.history;

import io.miragon.miravelo.leasing.application.port.inbound.PublishProcessEventUseCase;
import org.cibseven.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.springframework.stereotype.Component;

@Component
public class ProcessEventsPlugin extends AbstractProcessEnginePlugin {

    private final PublishProcessEventUseCase useCase;

    public ProcessEventsPlugin(PublishProcessEventUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        configuration.getCustomHistoryEventHandlers().add(new ProcessEventsHistoryHandler(useCase));
    }
}

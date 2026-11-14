package io.miragon.miravelo.leasing.adapter.inbound.cibseven.history;

import io.miragon.miravelo.leasing.application.port.inbound.PublishProcessMilestoneUseCase;
import org.cibseven.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.springframework.stereotype.Component;

@Component
public class ProcessMilestonePlugin extends AbstractProcessEnginePlugin {

    private final PublishProcessMilestoneUseCase useCase;

    public ProcessMilestonePlugin(PublishProcessMilestoneUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        configuration.getCustomHistoryEventHandlers().add(new ProcessMilestoneHistoryEventHandler(useCase));
    }
}

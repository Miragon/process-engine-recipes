package io.miragon.miravelo.leasing.adapter.inbound.cibseven.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.miragon.miravelo.leasing.application.port.inbound.PublishProcessMilestoneUseCase;
import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.cibseven.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.Test;

class ProcessMilestonePluginTest {

    private final PublishProcessMilestoneUseCase useCase = mock(PublishProcessMilestoneUseCase.class);
    private final ProcessMilestonePlugin underTest = new ProcessMilestonePlugin(useCase);

    @Test
    void addsTheMilestoneHandlerNextToTheDefaultHistory() {
        ProcessEngineConfigurationImpl configuration = new StandaloneInMemProcessEngineConfiguration();

        underTest.preInit(configuration);

        assertThat(configuration.getCustomHistoryEventHandlers())
                .singleElement()
                .isInstanceOf(ProcessMilestoneHistoryEventHandler.class);
        assertThat(configuration.isEnableDefaultDbHistoryEventHandler()).isTrue();
    }
}

package io.miragon.miravelo.leasing.adapter.inbound.cibseven.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.miragon.miravelo.leasing.application.port.inbound.PublishProcessEventUseCase;
import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.cibseven.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.Test;

class ProcessEventsPluginTest {

    private final PublishProcessEventUseCase useCase = mock(PublishProcessEventUseCase.class);
    private final ProcessEventsPlugin underTest = new ProcessEventsPlugin(useCase);

    @Test
    void addsTheHandlerNextToTheDefaultHistory() {
        ProcessEngineConfigurationImpl configuration = new StandaloneInMemProcessEngineConfiguration();

        underTest.preInit(configuration);

        assertThat(configuration.getCustomHistoryEventHandlers())
                .singleElement()
                .isInstanceOf(ProcessEventsHistoryHandler.class);
        assertThat(configuration.isEnableDefaultDbHistoryEventHandler()).isTrue();
    }
}

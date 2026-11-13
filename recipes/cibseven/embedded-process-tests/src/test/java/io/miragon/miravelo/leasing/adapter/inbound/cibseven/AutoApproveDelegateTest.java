package io.miragon.miravelo.leasing.adapter.inbound.cibseven;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.miragon.miravelo.leasing.application.port.inbound.AutoApproveApplicationUseCase;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

class AutoApproveDelegateTest {

    private final AutoApproveApplicationUseCase useCase = mock(AutoApproveApplicationUseCase.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final AutoApproveDelegate underTest = new AutoApproveDelegate(useCase);

    @Test
    void approvesTheApplicationOfTheProcessInstance() {
        when(execution.getProcessBusinessKey()).thenReturn("APP-1");

        underTest.execute(execution);

        verify(useCase).autoApprove("APP-1");
    }
}

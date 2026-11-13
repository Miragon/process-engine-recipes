package io.miragon.miravelo.leasing.adapter.inbound.cibseven;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.miragon.miravelo.leasing.application.port.inbound.HandOverBikeUseCase;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

class HandOverBikeDelegateTest {

    private final HandOverBikeUseCase useCase = mock(HandOverBikeUseCase.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final HandOverBikeDelegate underTest = new HandOverBikeDelegate(useCase);

    @Test
    void handsOverTheBikeOfTheOrderStoredInTheProcess() {
        when(execution.getProcessBusinessKey()).thenReturn("APP-1");
        when(execution.getVariable("orderId")).thenReturn("ORDER-1");

        underTest.execute(execution);

        verify(useCase).handOver("APP-1", "ORDER-1");
    }
}

package io.miragon.miravelo.leasing.adapter.inbound.cibseven;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.miragon.miravelo.leasing.application.port.inbound.OrderBikeUseCase;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

class OrderBikeDelegateTest {

    private final OrderBikeUseCase useCase = mock(OrderBikeUseCase.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final OrderBikeDelegate underTest = new OrderBikeDelegate(useCase);

    @Test
    void ordersTheBikeAndStoresTheOrderIdInTheProcess() {
        when(execution.getProcessBusinessKey()).thenReturn("APP-1");
        when(useCase.orderBike("APP-1")).thenReturn("ORDER-1");

        underTest.execute(execution);

        verify(execution).setVariable("orderId", "ORDER-1");
    }
}

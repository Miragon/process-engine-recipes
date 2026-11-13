package io.miragon.miravelo.leasing.adapter.inbound.cibseven;

import io.miragon.miravelo.leasing.application.port.inbound.OrderBikeUseCase;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("orderBikeDelegate")
public class OrderBikeDelegate implements JavaDelegate {

    private final OrderBikeUseCase useCase;

    public OrderBikeDelegate(OrderBikeUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String orderId = useCase.orderBike(execution.getProcessBusinessKey());
        execution.setVariable(ProcessVariables.ORDER_ID, orderId);
    }
}

package io.miragon.miravelo.leasing.adapter.inbound.cibseven;

import io.miragon.miravelo.leasing.application.port.inbound.HandOverBikeUseCase;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("handOverBikeDelegate")
public class HandOverBikeDelegate implements JavaDelegate {

    private final HandOverBikeUseCase useCase;

    public HandOverBikeDelegate(HandOverBikeUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String orderId = (String) execution.getVariable(ProcessVariables.ORDER_ID);
        useCase.handOver(execution.getProcessBusinessKey(), orderId);
    }
}

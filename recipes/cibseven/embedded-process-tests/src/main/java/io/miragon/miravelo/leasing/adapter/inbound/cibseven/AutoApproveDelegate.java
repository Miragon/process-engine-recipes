package io.miragon.miravelo.leasing.adapter.inbound.cibseven;

import io.miragon.miravelo.leasing.application.port.inbound.AutoApproveApplicationUseCase;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("autoApproveDelegate")
public class AutoApproveDelegate implements JavaDelegate {

    private final AutoApproveApplicationUseCase useCase;

    public AutoApproveDelegate(AutoApproveApplicationUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void execute(DelegateExecution execution) {
        useCase.autoApprove(execution.getProcessBusinessKey());
    }
}

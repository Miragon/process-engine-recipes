package io.miragon.miravelo.leasing.adapter.inbound.cibseven;

import io.miragon.miravelo.leasing.application.port.inbound.SendApprovalReminderUseCase;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("sendReminderDelegate")
public class SendReminderDelegate implements JavaDelegate {

    private final SendApprovalReminderUseCase useCase;

    public SendReminderDelegate(SendApprovalReminderUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void execute(DelegateExecution execution) {
        useCase.sendReminder(execution.getProcessBusinessKey());
    }
}

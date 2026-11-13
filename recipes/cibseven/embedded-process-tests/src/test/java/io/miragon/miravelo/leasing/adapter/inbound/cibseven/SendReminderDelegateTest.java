package io.miragon.miravelo.leasing.adapter.inbound.cibseven;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.miragon.miravelo.leasing.application.port.inbound.SendApprovalReminderUseCase;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

class SendReminderDelegateTest {

    private final SendApprovalReminderUseCase useCase = mock(SendApprovalReminderUseCase.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final SendReminderDelegate underTest = new SendReminderDelegate(useCase);

    @Test
    void remindsTheManagerAboutTheApplicationOfTheProcessInstance() {
        when(execution.getProcessBusinessKey()).thenReturn("APP-1");

        underTest.execute(execution);

        verify(useCase).sendReminder("APP-1");
    }
}

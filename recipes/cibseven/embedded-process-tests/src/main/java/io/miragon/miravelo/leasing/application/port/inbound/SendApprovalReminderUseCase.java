package io.miragon.miravelo.leasing.application.port.inbound;

public interface SendApprovalReminderUseCase {

    void sendReminder(String applicationId);
}

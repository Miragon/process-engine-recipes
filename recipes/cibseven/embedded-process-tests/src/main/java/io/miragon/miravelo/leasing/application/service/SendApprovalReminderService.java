package io.miragon.miravelo.leasing.application.service;

import io.miragon.miravelo.leasing.application.port.inbound.SendApprovalReminderUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SendApprovalReminderService implements SendApprovalReminderUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendApprovalReminderService.class);

    @Override
    public void sendReminder(String applicationId) {
        log.info("Manager reminded to approve application {}", applicationId);
    }
}

package io.miragon.miravelo.leasing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SendApprovalReminderServiceTest {

    private final SendApprovalReminderService underTest = new SendApprovalReminderService();

    @Test
    void remindsTheManager(CapturedOutput output) {
        underTest.sendReminder("APP-1");

        assertThat(output).contains("Manager reminded to approve application APP-1");
    }
}

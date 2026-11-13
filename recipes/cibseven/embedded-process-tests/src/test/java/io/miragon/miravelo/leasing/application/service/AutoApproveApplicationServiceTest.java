package io.miragon.miravelo.leasing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AutoApproveApplicationServiceTest {

    private final AutoApproveApplicationService underTest = new AutoApproveApplicationService();

    @Test
    void approvesTheApplicationAutomatically(CapturedOutput output) {
        underTest.autoApprove("APP-1");

        assertThat(output).contains("Application APP-1 approved automatically");
    }
}

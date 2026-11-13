package io.miragon.miravelo.leasing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class HandOverBikeServiceTest {

    private final HandOverBikeService underTest = new HandOverBikeService();

    @Test
    void handsOverTheBike(CapturedOutput output) {
        underTest.handOver("APP-1", "ORDER-1");

        assertThat(output).contains("Bike of order ORDER-1 handed over for application APP-1");
    }
}

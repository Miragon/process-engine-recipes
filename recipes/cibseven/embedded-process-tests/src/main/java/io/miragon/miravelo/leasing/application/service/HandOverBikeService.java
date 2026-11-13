package io.miragon.miravelo.leasing.application.service;

import io.miragon.miravelo.leasing.application.port.inbound.HandOverBikeUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HandOverBikeService implements HandOverBikeUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandOverBikeService.class);

    @Override
    public void handOver(String applicationId, String orderId) {
        log.info("Bike of order {} handed over for application {}", orderId, applicationId);
    }
}

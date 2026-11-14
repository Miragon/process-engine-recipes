package io.miragon.miravelo.leasing.application.service;

import io.miragon.miravelo.leasing.application.port.inbound.OrderBikeUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderBikeService implements OrderBikeUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderBikeService.class);

    @Override
    public void orderBike(String applicationId) {
        log.info("Bike for application {} ordered", applicationId);
    }
}

package io.miragon.miravelo.leasing.application.service;

import io.miragon.miravelo.leasing.application.port.inbound.OrderBikeUseCase;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderBikeService implements OrderBikeUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderBikeService.class);

    @Override
    public String orderBike(String applicationId) {
        String orderId = "ORDER-" + UUID.randomUUID();
        log.info("Bike for application {} ordered as {}", applicationId, orderId);
        return orderId;
    }
}

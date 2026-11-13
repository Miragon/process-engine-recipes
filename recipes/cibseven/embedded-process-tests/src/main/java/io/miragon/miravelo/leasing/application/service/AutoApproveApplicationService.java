package io.miragon.miravelo.leasing.application.service;

import io.miragon.miravelo.leasing.application.port.inbound.AutoApproveApplicationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AutoApproveApplicationService implements AutoApproveApplicationUseCase {

    private static final Logger log = LoggerFactory.getLogger(AutoApproveApplicationService.class);

    @Override
    public void autoApprove(String applicationId) {
        log.info("Application {} approved automatically", applicationId);
    }
}

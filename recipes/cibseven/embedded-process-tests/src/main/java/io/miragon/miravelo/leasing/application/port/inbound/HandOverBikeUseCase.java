package io.miragon.miravelo.leasing.application.port.inbound;

public interface HandOverBikeUseCase {

    void handOver(String applicationId, String orderId);
}

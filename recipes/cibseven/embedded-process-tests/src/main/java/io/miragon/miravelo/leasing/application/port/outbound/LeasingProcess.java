package io.miragon.miravelo.leasing.application.port.outbound;

public interface LeasingProcess {

    void startLeasing(String applicationId, double contractValue);

    void approveApplication(String applicationId);

    void prepareContract(String applicationId);
}

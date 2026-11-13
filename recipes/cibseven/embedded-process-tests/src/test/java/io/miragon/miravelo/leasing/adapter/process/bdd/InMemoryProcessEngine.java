package io.miragon.miravelo.leasing.adapter.process.bdd;

import java.util.UUID;
import org.cibseven.bpm.engine.ProcessEngineConfiguration;
import org.cibseven.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.cibseven.bpm.engine.test.junit5.ProcessEngineExtension;
import org.cibseven.bpm.engine.test.mock.MockExpressionManager;
import org.cibseven.community.process_test_coverage.engine.platform7.ProcessCoverageConfigurator;
import org.cibseven.community.process_test_coverage.junit5.platform7.ProcessEngineCoverageExtension;

final class InMemoryProcessEngine {

    private InMemoryProcessEngine() {
    }

    static ProcessEngineExtension withCoverage() {
        StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=1000");
        configuration.setHistory(ProcessEngineConfiguration.HISTORY_FULL);
        configuration.setJobExecutorActivate(false);
        configuration.setExpressionManager(new MockExpressionManager());
        ProcessCoverageConfigurator.initializeProcessCoverageExtensions(configuration);
        return ProcessEngineCoverageExtension.builder(configuration).build();
    }
}

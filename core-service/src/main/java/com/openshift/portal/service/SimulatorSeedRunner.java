package com.openshift.portal.service;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Seeds the simulated fleet, and the team aliases its namespaces use, at startup. With several replicas only the one holding the lock seeds; the others
 * skip and find the fleet already there.
 */
@Component
@ConditionalOnProperty(prefix = "openshift.portal.simulator", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SimulatorSeedRunner implements ApplicationRunner {

    private final AcmSimulatorService simulatorService;
    private final LockingTaskExecutor lockingTaskExecutor;

    @Override
    public void run(ApplicationArguments args) {
        lockingTaskExecutor.executeWithLock((Runnable) () -> {
                    simulatorService.seedInitialFleetIfEmpty();
                    simulatorService.ensureSimulatorAliases();
                },
                new LockConfiguration(Instant.now(), "simulator-seed", Duration.ofMinutes(5), Duration.ZERO));
    }
}

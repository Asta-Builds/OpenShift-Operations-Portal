package com.openshift.portal;

import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.service.AcmCollectorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class ScheduledCollectionIntegrationTest {

    @Autowired
    private AcmCollectorService collectorService;
    @Autowired
    private ClusterRepository clusterRepository;
    @Autowired
    private ClusterSnapshotRepository snapshotRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Every scheduled run keeps its lock for at least a minute (lockAtLeastFor). The database is shared by every test
     * context in this JVM, so the lock is also released afterwards: manual collections in other tests would get a 409.
     * Locks are expired, not deleted: ShedLock remembers the rows it created and only updates them afterwards.
     */
    @BeforeEach
    @AfterEach
    void releaseSchedulerLocks() {
        jdbcTemplate.update("UPDATE shedlock SET lock_until = TIMESTAMP '2000-01-01 00:00:00'");
    }

    @Test
    void scheduledRunCollectsEveryClusterWithoutLoggingErrors(CapturedOutput output) {
        long before = snapshotRepository.count();

        collectorService.scheduledCollection();

        assertThat(snapshotRepository.count() - before).isEqualTo(clusterRepository.count());
        assertThat(output.getOut()).doesNotContain(" ERROR ").doesNotContain("Exception");
    }

    @Test
    void twoInstancesCollectOncePerCycle() {
        try (ConfigurableApplicationContext first = startInstance();
             ConfigurableApplicationContext second = startInstance()) {
            long before = snapshotRepository.count();

            first.getBean(AcmCollectorService.class).scheduledCollection();
            second.getBean(AcmCollectorService.class).scheduledCollection();

            assertThat(snapshotRepository.count() - before).isEqualTo(clusterRepository.count());
        }
    }

    /** Another replica: a separate web application on a random port, sharing this test's in-memory database. */
    private static ConfigurableApplicationContext startInstance() {
        return new SpringApplicationBuilder(OpenshiftPortalApplication.class)
                .profiles("dev")
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0"); // command-line arguments outrank application.yml
    }
}

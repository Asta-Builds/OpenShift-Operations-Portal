package com.openshift.portal.service;

import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.HubSyncRun;
import com.openshift.portal.dto.SnapshotTriggerResultDto;
import com.openshift.portal.exception.CollectionInProgressException;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Collections started from the API. They take the scheduled collection's lock, so they never overlap a scheduled
 * cycle or each other on any replica: overlapping cycles would store duplicate snapshots and could register a newly
 * discovered cluster twice.
 */
@Service
@RequiredArgsConstructor
public class ManualCollectionService {

    /** As long as a scheduled cycle may hold the lock; released as soon as the collection ends. */
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(14);

    private final AcmCollectorService collectorService;
    private final LockingTaskExecutor lockingTaskExecutor;

    /** Every hub, as a scheduled cycle. */
    public SnapshotTriggerResultDto collectAll() {
        return locked(collectorService::triggerCollection);
    }

    public HubSyncRun collectHub(AcmHub hub) {
        return locked(() -> collectorService.collectHub(hub));
    }

    private <T> T locked(Supplier<T> collection) {
        LockingTaskExecutor.TaskResult<T> result;
        try {
            result = lockingTaskExecutor.executeWithLock(collection::get, new LockConfiguration(
                    Instant.now(), AcmCollectorService.COLLECTION_LOCK, LOCK_AT_MOST_FOR, Duration.ZERO));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
        if (!result.wasExecuted()) {
            // A scheduled cycle keeps the lock for at least a minute, even when it finishes sooner
            throw new CollectionInProgressException(
                    "Another collection is running or has just finished; try again in a minute.");
        }
        return result.getResult();
    }
}

package com.openshift.portal.acm;

import com.openshift.portal.domain.entity.AcmHub;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Runs calls to an ACM hub under that hub's own circuit breaker and retry, so one failing hub never trips the
 * breaker of another. Both are created on first use from the {@code acmHub} Resilience4j configs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HubResilience {

    public static final String CONFIG = "acmHub";

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    @PostConstruct
    void logRetries() {
        retryRegistry.getEventPublisher().onEntryAdded(added -> added.getAddedEntry().getEventPublisher()
                .onRetry(event -> log.warn("Call to {} failed (attempt {}), retrying in {} ms: {}",
                        event.getName(), event.getNumberOfRetryAttempts(), event.getWaitInterval().toMillis(),
                        event.getLastThrowable() != null ? event.getLastThrowable().getMessage() : "unknown error")));
    }

    /**
     * Retry wraps the breaker: every attempt is recorded by the breaker, and once it opens the remaining attempts
     * fail fast with {@link io.github.resilience4j.circuitbreaker.CallNotPermittedException}, which is not retried.
     */
    public <T> T call(AcmHub hub, Supplier<T> remoteCall) {
        String name = instanceName(hub);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name, CONFIG);
        Retry retry = retryRegistry.retry(name, CONFIG);
        return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, remoteCall)).get();
    }

    /** State of the hub's breaker in this instance; a hub that was never called reports CLOSED. */
    public CircuitBreaker.State circuitState(AcmHub hub) {
        return circuitBreakerRegistry.find(instanceName(hub))
                .map(CircuitBreaker::getState)
                .orElse(CircuitBreaker.State.CLOSED);
    }

    private static String instanceName(AcmHub hub) {
        return "hub:" + hub.getName();
    }
}

package com.artverse.guard;

import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * Semaphore-based concurrency gate for agent LLM runs.
 * <p>
 * Each agent run acquires a permit before making the LLM call and releases it
 * in a finally block. When no permits are available, the caller receives a 429
 * response immediately (non-blocking).
 */
@Component
public class AgentConcurrencyGate {

    private static final String BUSY = "当前智能体请求较多，请稍后再试";

    private final Semaphore permits;

    public AgentConcurrencyGate(ArtVerseProperties properties) {
        int maxConcurrentRuns = Math.max(1, properties.getAgent().getMaxConcurrentRuns());
        this.permits = new Semaphore(maxConcurrentRuns);
    }

    /**
     * Acquire a permit or reject immediately.
     *
     * @throws BusinessException(429) when no permits are available
     */
    public void acquireOrReject() {
        if (!permits.tryAcquire()) {
            throw new BusinessException(429, BUSY);
        }
    }

    /**
     * Release a previously acquired permit. Safe to call multiple times.
     */
    public void release() {
        permits.release();
    }

    /**
     * Number of permits currently available (for health checks / monitoring).
     */
    public int availablePermits() {
        return permits.availablePermits();
    }
}

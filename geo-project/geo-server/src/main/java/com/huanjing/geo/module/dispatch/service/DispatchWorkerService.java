package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.common.llm.LlmCapacityView;
import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchWorkerService {

    private final DispatchQueueService dispatchQueueService;
    private final DispatchTaskService dispatchTaskService;
    private final DispatchProperties dispatchProperties;
    private final ObjectProvider<LlmCapacityView> llmCapacityViewProvider;
    private final ObjectProvider<LlmPoolProperties> llmPoolPropertiesProvider;

    private ExecutorService executorService;
    private Semaphore localInFlightSlots;

    @PostConstruct
    public void init() {
        int concurrency = Math.max(dispatchProperties.getWorkerPollConcurrency(), 1);
        this.executorService = Executors.newFixedThreadPool(concurrency);
        this.localInFlightSlots = new Semaphore(resolveMaxInFlight(concurrency));
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Scheduled(fixedDelayString = "${geo.dispatch.worker-poll-ms:1000}")
    public void pollQueue() {
        int concurrency = Math.max(dispatchProperties.getWorkerPollConcurrency(), 1);
        for (int i = 0; i < concurrency; i++) {
            if (isPermitGovernorBusy()) {
                return;
            }
            if (!tryAcquireLocalSlot()) {
                return;
            }
            Long taskId;
            try {
                taskId = dispatchQueueService.popNextTaskId();
            } catch (RuntimeException ex) {
                releaseLocalSlot();
                throw ex;
            }
            if (taskId == null) {
                releaseLocalSlot();
                return;
            }
            submitTask(taskId);
        }
    }

    private void submitTask(Long taskId) {
        try {
            executorService.submit(() -> {
                try {
                    dispatchTaskService.processTask(taskId);
                } catch (Exception ex) {
                    log.error("Worker process task {} failed", taskId, ex);
                } finally {
                    releaseLocalSlot();
                }
            });
        } catch (RejectedExecutionException ex) {
            requeueClaimedTask(taskId, ex);
            releaseLocalSlot();
            throw ex;
        }
    }

    private void requeueClaimedTask(Long taskId, Exception cause) {
        try {
            dispatchTaskService.enqueueIfNeeded(taskId);
        } catch (Exception requeueEx) {
            log.error("Claimed dispatch task {} was rejected by worker executor and could not be re-queued", taskId, requeueEx);
            cause.addSuppressed(requeueEx);
        }
    }

    private boolean tryAcquireLocalSlot() {
        if (!dispatchProperties.isWorkerPopAdmissionEnabled()) {
            return true;
        }
        return localInFlightSlots.tryAcquire();
    }

    private void releaseLocalSlot() {
        if (!dispatchProperties.isWorkerPopAdmissionEnabled()) {
            return;
        }
        localInFlightSlots.release();
    }

    private int resolveMaxInFlight(int concurrency) {
        int configured = dispatchProperties.getWorkerMaxInFlight();
        return configured > 0 ? configured : concurrency;
    }

    private boolean isPermitGovernorBusy() {
        if (!dispatchProperties.isWorkerPermitGovernorEnabled()) {
            return false;
        }
        LlmCapacityView capacityView = llmCapacityViewProvider.getIfAvailable();
        LlmPoolProperties poolProperties = llmPoolPropertiesProvider.getIfAvailable();
        if (capacityView == null || poolProperties == null || !poolProperties.isEnabled()) {
            return false;
        }
        Long active;
        try {
            active = capacityView.activeGlobalCount();
        } catch (RuntimeException ex) {
            log.warn("Skip dispatch permit governor because LLM capacity view is unavailable", ex);
            return false;
        }
        if (active == null || active < 0L) {
            return false;
        }
        int limit = Math.max(1, poolProperties.getGlobalConcurrency());
        double ratio = active / (double) limit;
        return ratio >= dispatchProperties.getWorkerPermitGovernorBusyRatio();
    }
}

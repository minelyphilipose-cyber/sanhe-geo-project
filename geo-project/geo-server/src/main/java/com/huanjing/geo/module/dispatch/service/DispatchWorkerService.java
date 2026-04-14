package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchWorkerService {

    private final DispatchQueueService dispatchQueueService;
    private final DispatchTaskService dispatchTaskService;
    private final DispatchProperties dispatchProperties;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        int concurrency = Math.max(dispatchProperties.getWorkerConcurrency(), 1);
        this.executorService = Executors.newFixedThreadPool(concurrency);
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Scheduled(fixedDelayString = "${geo.dispatch.worker-poll-ms:1000}")
    public void pollQueue() {
        int concurrency = Math.max(dispatchProperties.getWorkerConcurrency(), 1);
        for (int i = 0; i < concurrency; i++) {
            Long taskId = dispatchQueueService.popNextTaskId();
            if (taskId == null) {
                return;
            }
            executorService.submit(() -> {
                try {
                    dispatchTaskService.processTask(taskId);
                } catch (Exception ex) {
                    log.error("Worker process task {} failed", taskId, ex);
                }
            });
        }
    }
}

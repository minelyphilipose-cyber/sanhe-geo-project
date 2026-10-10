package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.ProjectSelfMediaPlanningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ProjectSelfMediaPlanningJob {
    private final ProjectSelfMediaPlanningService planningService;
    private final Executor executor;
    private final AtomicBoolean working = new AtomicBoolean();

    public ProjectSelfMediaPlanningJob(ProjectSelfMediaPlanningService planningService,
                                       @Qualifier("selfMediaPlanningExecutor") Executor executor) {
        this.planningService = planningService;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${geo.self-media.auto-schedule.planning-poll-ms:3000}")
    public void processPending() {
        if (!working.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try { planningService.processNext(); }
                catch (RuntimeException ex) { log.warn("self-media planning worker failed", ex); }
                finally { working.set(false); }
            });
        } catch (RuntimeException ex) {
            working.set(false);
            log.warn("self-media planning worker unavailable; persisted requests will retry", ex);
        }
    }
}

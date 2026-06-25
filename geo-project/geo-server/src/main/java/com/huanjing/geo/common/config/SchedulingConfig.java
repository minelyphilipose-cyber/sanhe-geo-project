package com.huanjing.geo.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@Slf4j
public class SchedulingConfig {

    @Value("${geo.scheduling.pool-size:8}")
    private int poolSize;

    @Value("${geo.scheduling.await-termination-seconds:30}")
    private int awaitTerminationSeconds;

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(2, poolSize));
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(Math.max(1, awaitTerminationSeconds));
        scheduler.setErrorHandler(error -> log.error("scheduled task failed", error));
        scheduler.initialize();
        return scheduler;
    }
}

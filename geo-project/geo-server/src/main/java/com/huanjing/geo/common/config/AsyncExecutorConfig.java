package com.huanjing.geo.common.config;

import com.huanjing.geo.module.audit.service.AuditMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncExecutorConfig {

    private static final Logger AUDIT_EXECUTOR_LOGGER = LoggerFactory.getLogger("security.audit");

    @Value("${presale.judge.concurrent-platforms:4}")
    private int presaleJudgeConcurrentPlatforms;

    @Value("${presale.generate.threadpool.generate.core-pool-size:4}")
    private int presaleGenerateCorePoolSize;

    @Value("${presale.generate.threadpool.generate.max-pool-size:8}")
    private int presaleGenerateMaxPoolSize;

    @Value("${presale.generate.threadpool.generate.queue-capacity:200}")
    private int presaleGenerateQueueCapacity;

    @Value("${presale.generate.threadpool.platform.core-pool-size:10}")
    private int presalePlatformCorePoolSize;

    @Value("${presale.generate.threadpool.platform.max-pool-size:16}")
    private int presalePlatformMaxPoolSize;

    @Value("${presale.generate.threadpool.platform.queue-capacity:50}")
    private int presalePlatformQueueCapacity;

    @Value("${presale.generate.threadpool.keep-alive-seconds:60}")
    private int presaleThreadPoolKeepAliveSeconds;

    @Value("${presale.generate.threadpool.await-termination-seconds:30}")
    private int presaleThreadPoolAwaitTerminationSeconds;

    @Value("${audit.threadpool.core-pool-size:2}")
    private int auditCorePoolSize;

    @Value("${audit.threadpool.max-pool-size:4}")
    private int auditMaxPoolSize;

    @Value("${audit.threadpool.queue-capacity:1000}")
    private int auditQueueCapacity;

    @Bean("presaleGenerateExecutor")
    public Executor presaleGenerateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, presaleGenerateCorePoolSize));
        executor.setMaxPoolSize(Math.max(presaleGenerateCorePoolSize, presaleGenerateMaxPoolSize));
        executor.setQueueCapacity(Math.max(0, presaleGenerateQueueCapacity));
        executor.setKeepAliveSeconds(Math.max(1, presaleThreadPoolKeepAliveSeconds));
        executor.setThreadNamePrefix("presale-generate-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.max(1, presaleThreadPoolAwaitTerminationSeconds));
        executor.initialize();
        return executor;
    }

    @Bean("presalePlatformExecutor")
    public Executor presalePlatformExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, presalePlatformCorePoolSize));
        executor.setMaxPoolSize(Math.max(presalePlatformCorePoolSize, presalePlatformMaxPoolSize));
        executor.setQueueCapacity(Math.max(0, presalePlatformQueueCapacity));
        executor.setKeepAliveSeconds(Math.max(1, presaleThreadPoolKeepAliveSeconds));
        executor.setThreadNamePrefix("presale-platform-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.max(1, presaleThreadPoolAwaitTerminationSeconds));
        executor.initialize();
        return executor;
    }

    @Bean("presaleJudgeExecutor")
    public Executor presaleJudgeExecutor() {
        int poolSize = Math.max(1, presaleJudgeConcurrentPlatforms);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("presale-judge-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("auditExecutor")
    public Executor auditExecutor(AuditMetrics auditMetrics) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, auditCorePoolSize));
        executor.setMaxPoolSize(Math.max(auditCorePoolSize, auditMaxPoolSize));
        executor.setQueueCapacity(Math.max(1, auditQueueCapacity));
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("audit-");
        executor.setRejectedExecutionHandler((runnable, executorService) -> {
            auditMetrics.incrementExecutorRejected();
            AUDIT_EXECUTOR_LOGGER.warn("auditExecutor queue full, running audit task on caller thread");
            new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(runnable, executorService);
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

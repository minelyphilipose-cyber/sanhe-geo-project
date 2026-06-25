package com.huanjing.geo.common.llm.monitoring.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class LlmRuntimeConfigVO {
    private DispatchConfig dispatch = new DispatchConfig();
    private LlmPoolConfig llmPool = new LlmPoolConfig();
    private MobileJudgeConfig mobileJudge = new MobileJudgeConfig();
    private ArticleRoutingConfig articleRouting = new ArticleRoutingConfig();

    @Data
    public static class DispatchConfig {
        private int questionPollCycleDays;
        private int workerPollConcurrency;
        private boolean workerPopAdmissionEnabled;
        private int workerMaxInFlight;
        private boolean workerPermitGovernorEnabled;
        private double workerPermitGovernorBusyRatio;
        private boolean capacityFailureClassificationEnabled;
        private boolean resourceBusyRetryAfterEnabled;
        private int resourceBusyRetryMinSeconds;
        private int resourceBusyRetryJitterSeconds;
        private int resourceBusyRetryMaxSeconds;
        private int resourceBusyMaxAttempts;
        private StaggerConfig stagger = new StaggerConfig();
    }

    @Data
    public static class StaggerConfig {
        private boolean enabled;
        private String taskTypes;
        private int windowMinutes;
        private int maxDelayMinutes;
        private int jitterSeconds;
        private int capJitterSeconds;
        private long maxQueueSize;
        private String overflowPolicy;
        private Map<String, ?> platforms;
    }

    @Data
    public static class LlmPoolConfig {
        private boolean enabled;
        private int globalConcurrency;
        private boolean blockingAcquireFailFastEnabled;
        private Set<String> blockingAcquireFailFastFeatures;
        private Map<String, Integer> featureConcurrency;
    }

    @Data
    public static class MobileJudgeConfig {
        private boolean enabled;
        private int maxProjectsPerRun;
        private int perProjectLimit;
        private long workerMs;
        private List<String> platformCodes;
    }

    @Data
    public static class ArticleRoutingConfig {
        private List<String> excludedPlatformCodes;
    }
}

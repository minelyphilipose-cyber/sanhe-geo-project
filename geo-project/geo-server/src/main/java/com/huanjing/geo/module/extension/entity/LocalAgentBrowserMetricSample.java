package com.huanjing.geo.module.extension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("local_agent_browser_metric_sample")
public class LocalAgentBrowserMetricSample {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long localAgentSessionId;
    private String machineId;
    private String activeProfile;
    private String helperBootId;
    private Long browserEnvironmentId;
    private String environmentKey;
    private String providerProfileId;
    private String browserSessionEpoch;
    private LocalDateTime observedAt;
    private Long observedAtEpochMs;
    private String observationStatus;
    private LocalDateTime lastSuccessfulObservedAt;
    private Integer failedProbeDurationMs;
    private Long helperUptimeSeconds;
    private Integer retainedTaskCount;
    private Integer activeTaskCount;
    private Long claimedTotal;
    private Long executionClaimedTotal;
    private Long executionStartedTotal;
    private Long publishCheckClaimedTotal;
    private Long publishCheckStartedTotal;
    private Long completedTotal;
    private Long failedTotal;
    private Integer totalTargetCount;
    private Integer managedTargetCount;
    private Integer operatorTargetCount;
    private Integer unknownTargetCount;
    private Long processRssBytes;
    private Double processCpuPercent;
    private Integer processHandleCount;
    private Integer cdpConnectMs;
    private Integer cdpBrowserGetVersionMs;
    private Integer cdpBrowserPagesMs;
    private Integer networkEnableTimeoutCount;
    private Integer cdpDisconnectCount;
    private Integer extensionInjectionErrorCount;
    private Integer pageTimeoutCount;
    private Integer cdpProtocolTimeoutCount;
    private String metricsJson;
    private LocalDateTime createdAt;
}

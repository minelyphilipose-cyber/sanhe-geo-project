package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("poll_invocation_attempts")
public class PollInvocationAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pollResultId;
    private Long shardItemId;
    private Long dispatchTaskId;
    private Integer attemptNo;
    private Integer chainNo;
    private Long rootAttemptId;
    private Long retryOfAttemptId;
    private String triggerType;
    private Long projectId;
    private Long keywordResultId;
    private String questionSnapshot;
    private String systemPromptSnapshot;
    private Long platformConfigId;
    private String platformCode;
    private String channelCode;
    private String provider;
    private String integrationType;
    private String requestedModelId;
    private String responseModelId;
    private String modelVersion;
    private String endpointUrl;
    private String endpointId;
    private Long configVersion;
    private String providerConfigSnapshotJson;
    private String providerConfigHash;
    private String status;
    private String callStatus;
    private String searchStatus;
    private Boolean searchRequested;
    private Boolean searchTriggered;
    private Boolean generationSkipped;
    private String searchEvidenceJson;
    private String answer;
    private Boolean brandInSearch;
    private Boolean brandInAnswer;
    private String citationConfidence;
    private Boolean brandInformationValid;
    private String resultCode;
    private String brandDictionaryVersion;
    private String brandDictionarySnapshotJson;
    private String usageJson;
    private String errorCategory;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime lastHeartbeatAt;
    private LocalDateTime attemptDeadlineAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime finalizedAt;
    private Long latencyMs;
    private String adapterVersion;
    private String classifierVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

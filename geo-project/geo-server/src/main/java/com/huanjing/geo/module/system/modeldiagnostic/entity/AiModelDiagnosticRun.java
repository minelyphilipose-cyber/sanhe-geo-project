package com.huanjing.geo.module.system.modeldiagnostic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model_diagnostic_runs")
public class AiModelDiagnosticRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionRecordId;
    private String sessionId;
    private Integer turnNo;
    private Long operatorId;
    private String clientRequestId;
    private String requestFingerprint;

    private Long platformConfigId;
    private String platformCode;
    private String channelCode;
    private String platformName;
    private String usageScene;
    private String integrationType;
    private Long configVersion;
    private String configSnapshotJson;
    private String configSnapshotHash;
    private String endpointUrl;

    private String diagnosticMode;
    private String testMode;
    private String responseMode;
    private String probeCode;
    private String probeVersion;
    private String templateVersion;

    private String status;
    private String conclusion;
    private String conclusionReason;
    private String authenticationStatus;
    private String generationStatus;
    private String webSearchStatus;
    private String sourceParsingStatus;
    private String citationParsingStatus;
    private String evaluatorVersion;

    private String userMessage;
    private String systemPrompt;
    private String requestMessagesJson;
    private String answer;

    private String providerRequestId;
    private String requestedModelId;
    private String responseModelId;
    private Integer httpStatus;

    private String searchStatus;
    private String searchEvidenceJson;
    private String sourcesJson;
    private String citationsJson;
    private String usageJson;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer webSearchCallCount;
    private Integer sourceCount;
    private Integer validSourceCount;
    private Integer citationCount;
    private Integer validCitationCount;

    private String sanitizedRequest;
    private String sanitizedResponse;
    private String errorCategory;
    private String errorCode;
    private String errorMessage;

    private LocalDateTime deadlineAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

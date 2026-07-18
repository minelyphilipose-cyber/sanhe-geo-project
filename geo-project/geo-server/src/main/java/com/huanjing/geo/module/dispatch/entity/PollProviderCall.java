package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("poll_provider_calls")
public class PollProviderCall {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long attemptId;
    private String callType;
    private Integer sequenceNo;
    private Integer retryNo;
    private Long retryOfCallId;
    private String provider;
    private String endpointUrl;
    private String httpMethod;
    private String providerRequestId;
    private Integer httpStatus;
    private String status;
    private String errorCategory;
    private String errorCode;
    private String errorMessage;
    private Boolean retryable;
    private String sanitizedRequest;
    private String sanitizedResponse;
    private String rawRequestEncrypted;
    private String rawResponseEncrypted;
    private String payloadKeyVersion;
    private LocalDateTime rawPayloadPurgedAt;
    private String usageJson;
    private LocalDateTime deadlineAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long latencyMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

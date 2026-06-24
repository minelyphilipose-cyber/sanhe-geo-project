package com.huanjing.geo.common.llm.measurement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("llm_call_observation")
public class LlmCallObservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private Long customerId;
    private Long projectId;
    private String scope;
    private String normalizedPromptHash;
    private String feature;
    private String platformCode;
    private String platformName;
    private String modelId;
    private String modelName;
    private String governanceStack;
    private String routingStrategy;
    private String waitSemantics;
    private String status;
    private String errorCategory;
    private Integer httpStatusCode;
    private String providerErrorCode;
    private Long retryAfterMs;
    private String failureKind;
    private Integer requestCount;
    private Long waitMs;
    private Long httpMs;
    private Long totalMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal estimatedCost;
    private String currency;
    private LocalDateTime occurredAt;
}

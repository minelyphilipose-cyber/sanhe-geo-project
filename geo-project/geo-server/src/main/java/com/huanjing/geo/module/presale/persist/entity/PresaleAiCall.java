package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_ai_call")
public class PresaleAiCall {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long versionId;
    private Integer batchNo;
    private String platformCode;
    private String platformCodeSnapshot;
    private String platformNameSnapshot;
    private String modelIdSnapshot;
    private String modelNameSnapshot;
    private Long promptTemplateId;
    private String competitorName;
    private String stage;
    private Long parentCallId;
    private String requestPromptContent;
    private String queryContractVersion;
    private String searchEvidenceJson;
    private String callStatus;
    private Integer retryCount;
    private String rawResponse;
    private LocalDateTime payloadPurgedAt;
    private String failureReason;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer durationMs;
    private LocalDateTime createdAt;
}

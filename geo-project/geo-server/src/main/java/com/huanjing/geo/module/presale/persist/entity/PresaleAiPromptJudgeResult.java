package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("presale_ai_prompt_judge_result")
public class PresaleAiPromptJudgeResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long promptResultId;
    private Long versionId;
    private Integer batchNo;
    private String platformCode;
    private Long promptTemplateId;
    private String category;
    private String competitorName;
    private String judgeStatus;
    private Integer judgeAttemptCount;
    private String judgePlatformCode;
    private String judgeModelId;
    private BigDecimal judgeTemperature;
    private String judgeError;
    private String sentiment;
    private BigDecimal sentimentScore;
    private BigDecimal attributeHitRate;
    private String tone;
    private String preferredBrand;
    private String targetSentiment;
    private String reasoningQuality;
    private String attributesHit;
    private String factualErrors;
    private String targetAdvantages;
    private String targetDisadvantages;
    private String competitorAdvantages;
    private String judgePayloadJson;
    private String rawJudgeResponse;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

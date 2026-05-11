package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("keyword_group_result")
public class KeywordGroupResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String keywordText;
    private String questionCode;
    private String sourceType;
    private String seedText;
    private String questionTier;
    private Long sourceWorkorderId;
    private Long sourceBatchId;
    private Long sourceQuestionId;
    private Long sourceVersionId;
    private String sceneCode;
    private String priority;
    private String monitorFrequency;
    private java.math.BigDecimal scoreRelevance;
    private java.math.BigDecimal scoreIntent;
    private java.math.BigDecimal scoreCompetition;
    private java.math.BigDecimal scoreConversion;
    private java.math.BigDecimal scoreCoverage;
    private java.math.BigDecimal totalScore;
    private String relatedNeed;
    private String designReason;
    private String articleGenerationNote;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

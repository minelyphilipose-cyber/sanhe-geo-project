package com.huanjing.geo.module.geoquestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("geo_question_item")
public class GeoQuestionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workorderId;
    private Long batchId;
    private String questionText;
    private String sceneCode;
    private String tier;
    private String priority;
    private String monitorFrequency;
    private BigDecimal scoreRelevance;
    private BigDecimal scoreIntent;
    private BigDecimal scoreCompetition;
    private BigDecimal scoreConversion;
    private BigDecimal scoreCoverage;
    private BigDecimal totalScore;
    private Long relatedNeedId;
    private String relatedNeedText;
    private String designReason;
    private String status;
    private Integer replaceCount;
    private Integer sortOrder;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

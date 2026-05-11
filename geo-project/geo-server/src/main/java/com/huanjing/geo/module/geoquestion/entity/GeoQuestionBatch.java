package com.huanjing.geo.module.geoquestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("geo_question_batch")
public class GeoQuestionBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workorderId;
    private String batchNo;
    private Integer requestA;
    private Integer requestB;
    private Integer requestC;
    private Integer actualA;
    private Integer actualB;
    private Integer actualC;
    private Integer reservedA;
    private Integer reservedB;
    private Integer reservedC;
    private Integer activeRunningFlag;
    private String modelProvider;
    private String modelId;
    private String modelName;
    private String sceneWeightsJson;
    private BigDecimal temperature;
    private String promptSnapshot;
    private String llmResponseSnapshot;
    private String paramSnapshot;
    private String status;
    private String progressJson;
    private String errorMessage;
    private Boolean partialFlag;
    private Boolean cancelRequested;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

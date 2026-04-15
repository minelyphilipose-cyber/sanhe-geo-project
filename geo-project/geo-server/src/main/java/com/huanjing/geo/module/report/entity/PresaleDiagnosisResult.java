package com.huanjing.geo.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_diagnosis_results")
public class PresaleDiagnosisResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long projectId;
    private Long questionSetId;
    private Long questionItemId;
    private Long platformId;
    private String platformCode;
    private String status;
    private Integer requestCount;
    private Integer responseTimeMs;
    private Boolean brandHit;
    private Boolean siteMentioned;
    private Boolean contactMentioned;
    private String detailJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

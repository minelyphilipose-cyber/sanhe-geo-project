package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_report_poll_result")
public class BaselineReportPollResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long projectId;
    private Long keywordResultId;
    private String questionTier;
    private String questionText;
    private Long platformId;
    private String platformCode;
    private String platformName;
    private String status;
    private Integer requestCount;
    private Long responseTimeMs;
    private String responseText;
    private String errorMessage;
    private String detailJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

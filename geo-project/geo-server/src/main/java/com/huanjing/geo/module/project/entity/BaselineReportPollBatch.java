package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_report_poll_batch")
public class BaselineReportPollBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String status;
    private String selectedPlatformCodes;
    private String selectedQuestionTiers;
    private Integer platformCount;
    private Integer questionCount;
    private Integer totalCount;
    private Integer completedCount;
    private Integer failedCount;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

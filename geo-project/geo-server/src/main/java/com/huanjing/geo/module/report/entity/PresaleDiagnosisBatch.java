package com.huanjing.geo.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_diagnosis_batches")
public class PresaleDiagnosisBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long questionSetId;
    private Long dispatchTaskId;
    private String status;
    private Integer totalRequests;
    private Integer completedCount;
    private Integer failedCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

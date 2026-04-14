package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("poll_batches")
public class PollBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dispatchTaskId;
    private Long projectId;
    private LocalDate batchDate;
    private Integer batchNo;
    private LocalDateTime triggeredAt;
    private LocalDateTime finishedAt;
    private Integer totalQuestionCount;
    private Integer totalPlatformCount;
    private Integer questionCount;
    private Integer completedCount;
    private Integer failedCount;
    private Integer hitCount;
    private BigDecimal overallHitRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

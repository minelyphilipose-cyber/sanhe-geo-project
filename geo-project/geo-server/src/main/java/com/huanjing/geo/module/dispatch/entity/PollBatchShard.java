package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("poll_batch_shards")
public class PollBatchShard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long dispatchTaskId;
    private Long projectId;
    private Long platformId;
    private String platformCode;
    private String platformName;
    private LocalDate batchDate;
    private Integer batchNo;
    private String questionTier;
    private Integer shardNo;
    private String status;
    private Integer expectedCount;
    private Integer completedCount;
    private Integer failedCount;
    private Integer resourceWaitCount;
    private String lastError;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

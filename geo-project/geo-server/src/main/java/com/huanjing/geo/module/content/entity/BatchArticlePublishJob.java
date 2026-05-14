package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_batch_publish_job")
public class BatchArticlePublishJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String publishMode;
    private String status;
    private LocalDateTime scheduledAt;
    private Integer intervalMinutes;
    private Integer platformConcurrency;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}

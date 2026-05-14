package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_batch_publish_item")
public class BatchArticlePublishItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private Long articleId;
    private Long projectId;
    private String platformKey;
    private String contentStyle;
    private Long targetSiteId;
    private Long targetBrandId;
    private LocalDateTime plannedAt;
    private String status;
    private Long distributionTaskId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

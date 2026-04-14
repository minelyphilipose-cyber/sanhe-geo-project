package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("distribution_tasks")
public class DistributionTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long projectId;
    private Long siteId;
    private Integer attemptNo;
    private String status;
    private String integrationMethod;
    private String requestPayload;
    private String responsePayload;
    private String publishedUrl;
    private String errorMessage;
    private Integer retryCount;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}

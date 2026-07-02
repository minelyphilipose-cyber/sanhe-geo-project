package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("content_auto_distribution_plan")
public class ContentAutoDistributionPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long companyId;
    private Long brandId;
    private LocalDate planDate;
    private LocalDateTime plannedExecuteAt;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextAttemptAt;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

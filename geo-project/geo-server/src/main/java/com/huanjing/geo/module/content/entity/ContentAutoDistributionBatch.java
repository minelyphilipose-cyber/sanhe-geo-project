package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("content_auto_distribution_batch")
public class ContentAutoDistributionBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long companyId;
    private Long brandId;
    private LocalDate planDate;
    private String status;
    private Integer totalCount;
    private Integer generatedCount;
    private Integer scheduledCount;
    private Integer failedCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

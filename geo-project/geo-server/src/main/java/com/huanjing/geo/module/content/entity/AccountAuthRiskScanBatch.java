package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("account_auth_risk_scan_batch")
public class AccountAuthRiskScanBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scanType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private Integer skippedCount;
    private Long lastScannedId;
    private String errorSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

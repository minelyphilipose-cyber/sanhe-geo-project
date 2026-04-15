package com.huanjing.geo.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_report_snapshots")
public class PresaleReportSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Long diagnosisBatchId;
    private String snapshotData;
    private String diagnosisSummary;
    private String actionRecommendations;
    private String brandCompletenessChecks;
    private String questionMatrix;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

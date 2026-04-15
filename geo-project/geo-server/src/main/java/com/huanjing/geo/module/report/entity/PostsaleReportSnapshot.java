package com.huanjing.geo.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("postsale_report_snapshots")
public class PostsaleReportSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private String reportSubtype;
    private String summaryData;
    private String trendData;
    private String detailData;
    private String platformBreakdown;
    private String comparisonData;
    private String targetEvaluation;
    private String stageAdvice;
    private String stageAdviceInput;
    private String internalNotes;
    private String riskFlags;
    private String contentExecutionSummary;
    private String methodologyNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

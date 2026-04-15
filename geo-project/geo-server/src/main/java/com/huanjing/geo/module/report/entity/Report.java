package com.huanjing.geo.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reports")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String reportType;
    private Integer versionNo;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private String shareToken;
    private String sharePasswordHash;
    private LocalDateTime shareExpiresAt;
    private String pdfUrl;
    private LocalDateTime pdfGeneratedAt;
    private String visibility;
    private Long pairReportId;
    private Boolean isLatest;
    private String stageAdvice;
    private String stageAdviceInput;
    private Long supersededBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private LocalDateTime publishedAt;
    private Long publishedBy;
}

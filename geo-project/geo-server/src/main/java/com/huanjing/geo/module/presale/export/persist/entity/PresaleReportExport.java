package com.huanjing.geo.module.presale.export.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_report_export")
public class PresaleReportExport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Long versionId;
    private String idempotencyKey;
    private String exportProfile;
    private String fileFormat;
    private String status;
    private String errorMsg;
    private Integer retryCount;
    private Boolean cancelRequested;
    private String workerId;
    private String fileKey;
    private Long fileSize;
    private Integer filePages;
    private LocalDateTime expireAt;
    private String snapshotStorageType;
    private String snapshotJson;
    private String snapshotKey;
    private String renderTokenId;
    private String metricsJson;
    private Long triggerUserId;
    private LocalDateTime triggerAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

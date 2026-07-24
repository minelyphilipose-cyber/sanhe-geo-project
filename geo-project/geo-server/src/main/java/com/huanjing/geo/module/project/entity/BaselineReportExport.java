package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_report_export")
public class BaselineReportExport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private Long projectId;
    private String idempotencyKey;
    private String exportProfile;
    private String fileFormat;
    private String status;
    @TableField(value = "error_msg", updateStrategy = FieldStrategy.ALWAYS)
    private String errorMsg;
    @TableField(value = "worker_id", updateStrategy = FieldStrategy.ALWAYS)
    private String workerId;
    private String fileKey;
    private Long fileSize;
    private Integer filePages;
    private String snapshotJson;
    @TableField(value = "render_token_id", updateStrategy = FieldStrategy.ALWAYS)
    private String renderTokenId;
    private String metricsJson;
    private Long triggerUserId;
    private LocalDateTime triggerAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

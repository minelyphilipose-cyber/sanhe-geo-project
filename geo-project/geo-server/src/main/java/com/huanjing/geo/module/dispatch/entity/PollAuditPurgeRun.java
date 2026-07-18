package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("poll_audit_purge_runs")
public class PollAuditPurgeRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long requestedBy;
    private String purgeReason;
    private String scopeJson;
    private String status;
    private String affectedRowsJson;
    private String errorMessage;
    private LocalDateTime auditCommittedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

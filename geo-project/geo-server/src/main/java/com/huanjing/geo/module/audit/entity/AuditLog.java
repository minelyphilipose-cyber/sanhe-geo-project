package com.huanjing.geo.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String eventType;
    private String actorType;
    private Long actorId;
    private Long brandId;
    private Long accountId;
    private Long taskId;
    private String targetType;
    private String targetId;
    private String result;
    @TableField("`sensitive`")
    private Boolean sensitive;
    private String mode;
    private String ipAddress;
    private String userAgent;
    private Long extensionSessionId;
    private String deviceFingerprintHash;
    private String requestId;
    private String traceId;
    private String detailJson;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
}

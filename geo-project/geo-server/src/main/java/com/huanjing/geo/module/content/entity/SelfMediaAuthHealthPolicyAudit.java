package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_auth_health_policy_audit")
public class SelfMediaAuthHealthPolicyAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long policyId;
    private String platformCode;
    private String beforeJson;
    private String afterJson;
    private String changeReason;
    private Long changedBy;
    private LocalDateTime changedAt;
}

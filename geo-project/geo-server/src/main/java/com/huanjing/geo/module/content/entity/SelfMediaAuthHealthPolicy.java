package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_auth_health_policy")
public class SelfMediaAuthHealthPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platformCode;
    private Boolean enabled;
    private Integer reverifyIntervalDays;
    private Integer warningDays;
    private Integer credentialReferenceDays;
    private String credentialExpiryMode;
    private Boolean alertEnabled;
    private String defaultRecipientRole;
    private Integer version;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

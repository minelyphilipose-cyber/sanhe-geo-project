package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_login_verification")
public class SelfMediaLoginVerification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long selfMediaAccountId;
    private Long browserEnvironmentId;
    private Long browserEnvironmentAccountId;
    private String platform;
    private String expectedAccountName;
    private String expectedPlatformAccountId;
    private String status;
    private String resultCode;
    private String resultMessage;
    private String actualAccountName;
    private String actualPlatformAccountId;
    private String identityDiagnostics;
    private Long requestedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime reportedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

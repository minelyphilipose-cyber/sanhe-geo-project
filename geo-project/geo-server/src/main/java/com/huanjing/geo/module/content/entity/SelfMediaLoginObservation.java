package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_login_observation")
public class SelfMediaLoginObservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long selfMediaAccountId;
    private Long browserEnvironmentId;
    private Long browserEnvironmentAccountId;
    private Long loginVerificationId;
    private String platform;
    private String reportSource;
    private String observationResult;
    private String resultCode;
    private String resultMessage;
    private String actualAccountName;
    private String actualPlatformAccountId;
    private String currentUrl;
    private String extensionVersion;
    private String protocolVersion;
    private Boolean authoritative;
    private Long operatorId;
    private LocalDateTime createdAt;
}

package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_account")
public class SelfMediaAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String platform;
    private String platformAccountId;
    private String accountName;
    private String accountIdentity;
    private String status;
    private String authMode;
    private String scopeJson;
    @JsonIgnore
    private String accessTokenCipher;
    @JsonIgnore
    private String refreshTokenCipher;
    private String credentialKeyVersion;
    private LocalDateTime accessTokenExpiresAt;
    private LocalDateTime refreshTokenExpiresAt;
    private String avatarUrl;
    private String qrcodeUrl;
    private LocalDateTime lastAuthCheckedAt;
    private String lastAuthError;
    private String extraJson;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "NOW()")
    private LocalDateTime deletedAt;
    private Long deletedBy;
}

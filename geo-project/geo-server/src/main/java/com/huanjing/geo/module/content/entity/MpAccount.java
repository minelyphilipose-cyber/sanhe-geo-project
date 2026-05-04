package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mp_account")
public class MpAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String platform;
    private String accountName;
    private String authorizerAppid;
    @JsonIgnore
    private String authorizerRefreshTokenCipher;
    private String credentialKeyVersion;
    @JsonIgnore
    private String funcInfoJson;
    private String headImg;
    private String qrcodeUrl;
    private String status;
    private LocalDateTime lastAuthCheckedAt;
    private String lastAuthError;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.huanjing.geo.module.content.credential.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString(exclude = {"cookiesCiphertext", "encryptedDek"})
@TableName("self_media_cookie_credential")
public class SelfMediaCookieCredential {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long selfMediaAccountId;
    private Long brandId;
    private String platform;
    private Integer version;
    @JsonIgnore
    private String cookiesCiphertext;
    private String cookieIvBase64;
    @JsonIgnore
    private String encryptedDek;
    private String masterKeyId;
    private String cipherAlg;
    private String aadContext;
    private String userAgent;
    private String capturedFingerprintJson;
    @TableField("required_cookie_status")
    private String requiredCookieCheckJson;
    private Long capturedBy;
    private LocalDateTime capturedAt;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private LocalDateTime destroyedAt;
    private LocalDateTime createdAt;
}

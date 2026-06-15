package com.huanjing.geo.module.extension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("extension_session")
public class ExtensionSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long operatorId;
    private String tokenLookupHash;
    private String tokenHash;
    private String tokenHashAlg;
    private String tokenSalt;
    private String installId;
    private String environmentKey;
    private String providerProfileId;
    private String deviceFingerprintHash;
    private String deviceFingerprintHashAlg;
    private String extensionVersion;
    private String userAgent;
    private String status;
    private LocalDateTime boundAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long revokedBy;
}

package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SelfMediaAccountVO {
    private Long id;
    private Long brandId;
    private String platform;
    private String accountName;
    private String platformAccountId;
    private String avatarUrl;
    private String qrcodeUrl;
    private String status;
    private LocalDateTime lastAuthCheckedAt;
    private String lastAuthError;
    private String cookieCredentialStatus;
    private Integer cookieCredentialVersion;
    private LocalDateTime cookieCredentialCapturedAt;
    private String cookieCredentialIdentityStatus;
    private String cookieCredentialIdentityName;
    private String cookieCredentialIdentityMessage;

    public static SelfMediaAccountVO from(SelfMediaAccount account) {
        SelfMediaAccountVO vo = new SelfMediaAccountVO();
        vo.setId(account.getId());
        vo.setBrandId(account.getBrandId());
        vo.setPlatform(account.getPlatform());
        vo.setAccountName(account.getAccountName());
        vo.setPlatformAccountId(account.getPlatformAccountId());
        vo.setAvatarUrl(account.getAvatarUrl());
        vo.setQrcodeUrl(account.getQrcodeUrl());
        vo.setStatus(account.getStatus());
        vo.setLastAuthCheckedAt(account.getLastAuthCheckedAt());
        vo.setLastAuthError(account.getLastAuthError());
        return vo;
    }
}

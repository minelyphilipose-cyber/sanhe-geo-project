package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.MpAccount;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MpAccountVO {
    private Long id;
    private Long brandId;
    private String platform;
    private String accountName;
    private String authorizerAppid;
    private String headImg;
    private String qrcodeUrl;
    private String status;
    private LocalDateTime lastAuthCheckedAt;
    private String lastAuthError;

    public static MpAccountVO from(MpAccount account) {
        MpAccountVO vo = new MpAccountVO();
        vo.setId(account.getId());
        vo.setBrandId(account.getBrandId());
        vo.setPlatform(account.getPlatform());
        vo.setAccountName(account.getAccountName());
        vo.setAuthorizerAppid(account.getAuthorizerAppid());
        vo.setHeadImg(account.getHeadImg());
        vo.setQrcodeUrl(account.getQrcodeUrl());
        vo.setStatus(account.getStatus());
        vo.setLastAuthCheckedAt(account.getLastAuthCheckedAt());
        vo.setLastAuthError(account.getLastAuthError());
        return vo;
    }
}

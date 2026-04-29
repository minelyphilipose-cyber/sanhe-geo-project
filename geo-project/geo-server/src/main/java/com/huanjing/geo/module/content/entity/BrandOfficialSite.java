package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_official_site")
public class BrandOfficialSite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String siteName;
    private String siteDomain;
    private String cmsFrameworkCode;
    private String tenantKey;
    private String apiEndpoint;
    private String authType;
    private String credentialsCipher;
    private String status;
    private LocalDateTime lastCheckAt;
    private String lastCheckResult;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

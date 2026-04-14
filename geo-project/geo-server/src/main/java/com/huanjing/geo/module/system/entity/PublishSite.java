package com.huanjing.geo.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("publish_sites")
public class PublishSite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String siteName;
    private String domain;
    private String industryTags;
    private String tier;
    private String status;
    private String integrationMethod;
    private String apiEndpoint;
    private String httpMethod;
    private String authType;
    private String credentialRef;
    private String apiCredentialEncrypted;
    private String requestHeaderTemplate;
    private String requestBodyTemplate;
    private String responseUrlPath;
    private String contentConstraints;
    private String currentHealthStatus;
    private LocalDateTime lastFailureAt;
    private BigDecimal failureRate;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AuthorityMediaResourceVO {
    private Long id;
    private String resourceType;
    private String externalResourceId;
    private String name;
    private String platform;
    private String industry;
    private String province;
    private BigDecimal price;
    private Integer status;
    private Integer pcWeight;
    private Integer mWeight;
    private Integer newsResource;
    private Integer entranceLevel;
    private Integer includeCondition;
    private Integer publicationTime;
    private Integer weekendPublish;
    private String publishRate;
    private Integer inclusionRate;
    private String remark;
    private String entranceLink;
    private String caseLink;
    private Integer noDisclaimer;
    private Integer canSign;
    private Integer firstPublish;
    private Integer keep3Month;
    private String focalPic;
    private Long uptime;
    private LocalDateTime updatedAt;
}

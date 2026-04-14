package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand")
public class Brand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private String industry;
    private String brandName;
    private String brandSlug;
    private String mainBusiness;
    private String serviceArea;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String website;
    private String officialAccount;
    private String videoAccount;
    private String douyinAccount;
    private String phone;
    private String wechat;
    private String description;
    private String businessIntro;
    private String standardBrandStatement;
    private String businessStandardStatement;
    private String standardStatement;
    private String statementStatus;
    private LocalDateTime statementGeneratedAt;
    private LocalDateTime statementLockedAt;
    private Long statementLockedBy;
    private Integer statementVersion;
    private String statementHistory;
    private String forbiddenPhrases;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

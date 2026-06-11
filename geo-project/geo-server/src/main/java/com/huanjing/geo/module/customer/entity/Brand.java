package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String complianceIndustryCode;
    private String brandName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String brandShortName;
    private String brandSlug;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mainBusiness;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String coreProducts;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String brandPositioning;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String serviceArea;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String provinceCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String provinceName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cityCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cityName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String districtCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String districtName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String website;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String officialAccount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String videoAccount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String douyinAccount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String publicPhone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String publicAddress;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String selfMediaPublishLocationName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String wechat;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String businessIntro;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String brandQualificationDescription;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String brandCaseDescription;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String medicalLicense;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String diagnosisScope;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String institutionType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String practitionerInfoPublic;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String medicalAdReviewNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String complianceNotesMedical;
    private String standardStatement;
    private String statementStatus;
    private LocalDateTime statementGeneratedAt;
    private LocalDateTime statementLockedAt;
    private Long statementLockedBy;
    private Integer statementVersion;
    private String statementHistory;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String forbiddenPhrases;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String geoSiteCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String geoSiteStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String industrySiteName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String industrySiteCode;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long deletedBy;
}

package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("brand_offering")
public class BrandOffering {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String offeringName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String offeringAliasesJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String targetUsers;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String offeringIntro;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String qualificationDescription;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private String status;
    private Integer priority;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String useScenarios;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String medicalIndustryCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String medicalCategoryCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String medicalCategoryName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String qualificationRef;
    private Boolean medicalProjectEnabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}

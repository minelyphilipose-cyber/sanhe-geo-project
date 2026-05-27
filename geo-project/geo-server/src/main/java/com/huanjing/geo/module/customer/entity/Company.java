package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("company")
public class Company {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String companyName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactPhone;
    private String industry;
    private String industryTags;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String businessDirection;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String competitors;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String officialWebsite;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String officialAccount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String videoAccount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String douyinAccount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String city;
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
    private String serviceArea;
    private String ownerType;
    private String sourceType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long partnerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String partnerName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOwnerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String referralSource;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private Long createdBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long deletedBy;
}

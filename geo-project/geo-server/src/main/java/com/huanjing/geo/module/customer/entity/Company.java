package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private String contactName;
    private String contactPhone;
    private String industry;
    private String businessDirection;
    private String competitors;
    private String officialWebsite;
    private String officialAccount;
    private String videoAccount;
    private String douyinAccount;
    private String city;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String serviceArea;
    private String ownerType;
    private String sourceType;
    private Long partnerId;
    private String partnerName;
    private Long salesOwnerId;
    private String referralSource;
    private String status;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

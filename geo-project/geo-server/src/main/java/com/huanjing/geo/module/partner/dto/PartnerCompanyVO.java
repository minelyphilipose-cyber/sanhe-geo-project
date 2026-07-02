package com.huanjing.geo.module.partner.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PartnerCompanyVO {
    private Long id;
    private String companyName;
    private String contactName;
    private String contactPhone;
    private String industry;
    private String industryTags;
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
    private String referralSource;
    private String status;
    private String remark;
    private Long partnerStaffOwnerId;
    private String partnerStaffOwnerName;
    private String partnerStaffOwnerUsername;
    private Boolean partnerStaffOwnerActive;
    private Long activePackageBindingId;
    private String activePackageName;
    private String partnerWorkflowStatus;
    private LocalDateTime partnerWorkflowUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

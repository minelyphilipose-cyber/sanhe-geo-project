package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BrandCreateRequest {
    @NotNull
    private Long companyId;
    @NotBlank
    private String industry;
    @Size(max = 32)
    private String complianceIndustryCode;
    private List<String> coverableIndustries;
    private Boolean allowThirdPartyPromotion;
    @NotBlank
    private String brandName;
    @Size(max = 128)
    private String brandShortName;
    private String brandSlug;
    private String mainBusiness;
    @Size(max = 500)
    private String coreProducts;
    @Size(max = 255)
    private String brandPositioning;
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
    private String publicPhone;
    private String publicAddress;
    @Size(max = 64)
    private String selfMediaPublishLocationName;
    private String wechat;
    @Size(max = 1000)
    private String description;
    @Size(max = 1000)
    private String businessIntro;
    @Size(max = 1000)
    private String brandQualificationDescription;
    @Size(max = 1000)
    private String brandCaseDescription;
    @Size(max = 500)
    private String medicalLicense;
    @Size(max = 1000)
    private String diagnosisScope;
    @Size(max = 128)
    private String institutionType;
    private String practitionerInfoPublic;
    @Size(max = 128)
    private String medicalAdReviewNo;
    private String complianceNotesMedical;
    private String forbiddenPhrases;
    private String geoSiteCode;
    private String geoSiteStatus;
    private String geoSiteName;
    private String geoSiteDomain;
    private String industrySiteName;
    private String industrySiteCode;
    private String status;
    private String versionChangeReason;
}

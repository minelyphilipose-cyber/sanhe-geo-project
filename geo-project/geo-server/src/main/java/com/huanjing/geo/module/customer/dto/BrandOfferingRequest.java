package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrandOfferingRequest {
    @NotBlank(message = "请输入产品名称")
    @Size(max = 64, message = "产品名称最多64个字")
    private String offeringName;
    @Size(max = 120, message = "产品简称最多120个字")
    private String offeringAliases;
    @Size(max = 200, message = "目标人群最多200个字")
    private String targetUsers;
    @Size(max = 400, message = "产品介绍最多400个字")
    private String offeringIntro;
    @Size(max = 400, message = "产品资质描述最多400个字")
    private String qualificationDescription;
    @Size(max = 200, message = "备注最多200个字")
    private String remark;
    @NotBlank(message = "请选择产品状态")
    private String status;
    @NotNull(message = "请填写优先级")
    private Integer priority;
    @Size(max = 200, message = "适用场景最多200个字")
    private String useScenarios;
    @Size(max = 32)
    private String medicalIndustryCode;
    @Size(max = 64)
    private String medicalCategoryCode;
    @Size(max = 128)
    private String medicalCategoryName;
    @Size(max = 300, message = "特殊行业资质引用最多300个字")
    private String qualificationRef;
    private Boolean medicalProjectEnabled;
}

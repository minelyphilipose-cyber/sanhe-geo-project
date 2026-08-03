package com.huanjing.geo.module.presale.dto.request;

import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LlmPromptQuestionGenerateRequest {
    @NotBlank(message = "品牌名不能为空")
    @Size(max = PresaleReportInputLimits.BRAND_NAME_MAX_LENGTH, message = "品牌名最多 18 字")
    private String brandName;

    @NotBlank(message = "行业不能为空")
    private String industry;

    @NotBlank(message = "身份不能为空")
    @Size(max = PresaleReportInputLimits.INDUSTRY_ROLE_MAX_LENGTH, message = "身份最多 50 字")
    private String industryRole;

    @NotBlank(message = "地区不能为空")
    @Size(max = 50, message = "地区最多 50 字")
    private String region;

    @Size(max = 50, message = "目标用户最多 50 字")
    private String userType;

    @Size(max = 500, message = "客户诉求最多 500 字")
    private String userDemand;

    @NotNull(message = "totalCount 不能为空")
    private Integer totalCount;

    @NotNull(message = "categoryCounts 不能为空")
    private Map<PresalePromptCategoryCode, Integer> categoryCounts;

    @Valid
    private List<LlmPromptQuestionDraftRequest> existingQuestions;
}

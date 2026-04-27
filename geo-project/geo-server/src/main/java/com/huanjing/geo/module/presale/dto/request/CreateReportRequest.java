package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建报告请求。对应 UI 页 2 创建表单。
 */
@Data
public class CreateReportRequest {

    @NotBlank(message = "品牌名不能为空")
    @Size(max = 100, message = "品牌名最多 100 字")
    private String brandName;

    /** 行业字典 key。Service 层校验是否存在于字典。 */
    @NotBlank(message = "行业不能为空")
    private String industry;

    /** 身份字典 key。 */
    @NotBlank(message = "身份不能为空")
    private String industryRole;

    @NotBlank(message = "地区不能为空")
    @Size(max = 50, message = "地区最多 50 字")
    private String region;

    /** 客户诉求,可选,最多 500 字。 */
    @Size(max = 500, message = "客户诉求最多 500 字")
    private String userDemand;

    /** 目标用户/消费群体,可选,最多 50 字。 */
    @Size(max = 50, message = "目标用户最多 50 字")
    private String userType;

    /** 前端反显时加载到的 Prompt 模板版本,用于防止提交前全局 active version 变化。 */
    @NotBlank(message = "Prompt 模板版本不能为空")
    private String promptTemplateVersion;

    /** 固定数量 Prompt 草稿。首版只允许修改 promptContent,不允许增删排序。 */
    @NotEmpty(message = "Prompt 清单不能为空")
    private List<PromptTemplateDraftRequest> promptTemplates;
}

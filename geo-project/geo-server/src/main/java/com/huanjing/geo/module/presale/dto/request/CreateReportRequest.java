package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建报告请求。对应 UI 页 2 创建表单。
 */
@Data
public class CreateReportRequest {

    /** 合伙人侧幂等请求号。合伙人创建诊断报告时必填；内部创建可为空。 */
    @Size(max = 128, message = "请求号最多 128 字")
    private String requestId;

    @NotBlank(message = "品牌名不能为空")
    @Size(max = PresaleReportInputLimits.BRAND_NAME_MAX_LENGTH, message = "品牌名最多 18 字")
    private String brandName;

    /** 品牌曾用名,可选,最多 3 个。仅用于竞品统计排除,不参与本品牌提及判断。 */
    private List<@Size(max = 100, message = "品牌曾用名最多 100 字") String> brandFormerNames;

    /** 行业字典 key。Service 层校验是否存在于字典。 */
    @NotBlank(message = "行业不能为空")
    @Size(max = 50, message = "行业最多 50 字")
    private String industry;

    /** 身份字典 key。 */
    @NotBlank(message = "身份不能为空")
    @Size(max = PresaleReportInputLimits.INDUSTRY_ROLE_MAX_LENGTH, message = "身份最多 50 字")
    private String industryRole;

    /** 代理/经销类客户所代理的品牌,可选,最多 10 个；不作为目标品牌提及。 */
    @Size(max = PresaleReportInputLimits.REPRESENTED_BRAND_MAX_COUNT, message = "代理品牌最多 10 个")
    private List<@Size(max = PresaleReportInputLimits.REPRESENTED_BRAND_MAX_LENGTH,
            message = "代理品牌最多 100 字") String> representedBrands;

    @NotBlank(message = "地区不能为空")
    @Size(max = 50, message = "地区最多 50 字")
    private String region;

    /** 客户诉求,可选,最多 500 字。 */
    @Size(max = 500, message = "客户诉求最多 500 字")
    private String userDemand;

    /** 目标用户/消费群体,可选,最多 50 字。 */
    @Size(max = 50, message = "目标用户最多 50 字")
    private String userType;

    /** 客户指定竞品。为空时系统自动识别;非空时必须正好 3 个。 */
    private List<@Size(max = 100, message = "竞品名称最多 100 字") String> specifiedCompetitors;

    /** 前端反显时加载到的 Prompt 模板版本,用于防止提交前全局 active version 变化。 */
    private String promptTemplateVersion;

    /** 固定数量 Prompt 草稿。首版只允许修改 promptContent,不允许增删排序。 */
    private List<PromptTemplateDraftRequest> promptTemplates;

    /** 当前 Tab 决定提交来源: template / llm。空值兼容旧前端,按 template 处理。 */
    private String promptSourceMode;

    private LlmPromptQuestionPlanRequest llmQuestionPlan;

    private List<LlmPromptQuestionDraftRequest> llmPromptQuestions;
}

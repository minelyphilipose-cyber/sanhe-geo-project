package com.huanjing.geo.module.presale.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 新建售前报告页的诊断范围预览。
 */
@Data
@Builder
public class ReportScopePreviewVO {
    /** 当前启用的售前 AI 平台数。 */
    private Integer platformCount;

    /** 当前启用模板版本下的普通 Prompt 数。 */
    private Integer genericPromptCount;

    /** 当前启用模板版本下的竞品变量 Prompt 数。 */
    private Integer competitorPromptCount;

    /** 展示口径:普通 Prompt + 竞品变量 Prompt。 */
    private Integer promptQueryCount;

    /** 生成阶段的 LLM 调用上限,与创建报告时写入 total_llm_calls 的口径一致。 */
    private Integer llmCallUpperBound;

    /** 分析维度数。 */
    private Integer dimensionCount;
}

package com.huanjing.geo.module.presale.service;

public final class PresaleLlmPromptQuestionPrompts {
    private PresaleLlmPromptQuestionPrompts() {
    }

    public static final String SYSTEM_PROMPT = "你是售前 GEO 报告的问题生成助手。请严格根据用户给定的品牌、行业、身份、地区、目标用户和客户诉求生成中文自然问题。";

    public static final String USER_PROMPT_TEMPLATE = """
            请为售前 GEO 报告生成问题。

            基础信息:
            - 品牌: %s
            - 行业: %s
            - 身份: %s
            - 地区: %s
            - 目标用户: %s
            - 客户诉求: %s

            本次只需要补充以下数量的问题:
            %s

            问题类型生成指引:
            %s

            硬性要求:
            1. 输出严格 JSON 数组,不要 Markdown,不要解释。
            2. 每个元素格式为 {"categoryCode":"RECOMMENDATION","promptContent":"问题文本"}。
            3. COMPARISON 对比型问题必须包含 {competitor}。
            4. 非 COMPARISON 问题禁止包含 {competitor}。
            5. 除 COMPARISON 的 {competitor} 外,禁止输出任何占位符,包括但不限于 {brand}、{product}、{industry}、{industry_role}、{region}、{user_type};需要品牌、行业、地区等信息时直接使用基础信息中的真实文本。
            6. 所有问题用于模拟真实用户向大模型提问的场景,需保持真实用户口吻:口语化但保留必要行业术语。
            7. PROBLEM 问题型禁止直接提及基础信息中的品牌名称,必须以行业、地区、具体场景或用户痛点发问,用于观察 AI 是否会自然返回当前品牌。
            8. 每条 6-80 字,中文自然问句,避免重复、空泛和模板腔。
            9. 避免与以下已有问题重复或语义重复:
            %s
            """;
}

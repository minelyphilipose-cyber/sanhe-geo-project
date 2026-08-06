package com.huanjing.geo.module.presale.service;

public final class PresaleLlmPromptQuestionPrompts {
    private PresaleLlmPromptQuestionPrompts() {
    }

    public static final String SYSTEM_PROMPT = "你是售前 GEO（生成式引擎优化）报告的问题生成助手。请严格根据用户给定的品牌、行业、身份、地区、目标用户和客户诉求生成自然、简短、真实的中文用户问题。";

    public static final String USER_PROMPT_TEMPLATE = """
            请为售前 GEO（生成式引擎优化）报告生成问题。

            基础信息:
            - 品牌: %s
            - 行业: %s
            - 身份: %s
            - 代理品牌: %s
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
            7. 每条问题必须明确包含地区中的真实地名，不能只写“本地”“附近”。推荐型和场景型必须以行业、品类或用户需求为主体，不得出现任何具体品牌名（包括客户品牌、代理品牌、竞品或示例品牌）；问题型禁止出现客户品牌和代理品牌；认知型、对比型可以出现品牌名称。
            8. 推荐型、对比型、问题型、认知型最多 25 个中文字符；场景型最多 30 个中文字符。对比型的 {competitor} 按 4 个中文字符计算。
            9. 问题应像真实用户口吻，简短、自然、可直接向 AI 提问，并以问号结尾。推荐型表达选择/推荐诉求；问题型必须同时包含真实顾虑和明确的决策追问（如哪些品牌、哪家服务商、怎么选更稳妥），使 AI 有理由自然推荐品牌或服务商，禁止只问“会不会有问题/靠不靠谱”；场景型包含预算、人群、使用时机等具体条件。
            10. 仅当身份为代理商/经销商等且代理品牌非“无”时：产品、品牌、方案、选购类问题应允许自然推荐代理品牌；本地服务、渠道、门店、交付类问题应允许自然推荐客户经营主体。以上规则必须依据当前行业动态表达，不得使用固定行业话术。
            11. 避免与以下已有问题重复或语义重复:
            %s
            """;
}

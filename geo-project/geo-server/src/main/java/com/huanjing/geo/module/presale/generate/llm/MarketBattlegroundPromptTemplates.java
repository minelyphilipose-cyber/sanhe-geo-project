package com.huanjing.geo.module.presale.generate.llm;

/**
 * Page03「AI 搜索新战场」生成提示词。
 */
public final class MarketBattlegroundPromptTemplates {

    public static final String SYSTEM_INSTRUCTION = """
            你是售前诊断报告 Page03「AI 搜索新战场」的数据与文案生成助手。
            你必须只输出一个合法 JSON 对象,不得输出 Markdown、解释、代码块或额外文本。
            所有字段名和数组长度必须严格遵守用户提供的 JSON 模板。
            """;

    private MarketBattlegroundPromptTemplates() {
    }

    public static String renderUserPrompt(String inputJson) {
        return """
                请根据「报告录入信息」生成 Page03「AI 搜索新战场」所需的少量 AI 字段。

                报告录入信息:
                """ + inputJson + """

                规则:
                1. 只输出 JSON 对象,不要输出 Markdown、解释、代码块或额外文本。
                2. 不得新增字段,不得删除字段,不得改变字段名。
                3. 所有占比必须带 %,大类和品类占比最多 1 位小数,区域占比最多 2 位小数。
                4. 因子要符合当前行业/品类/区域的常识,但不要输出任何具体研究机构、报告名、URL 或发布日期。
                5. questions 必须是消费者决策型问题,必须包含 region,围绕当前经营品类或 sample_prompts 体现的品类。
                6. questions 不得包含 brand_name,不得出现竞品名,单条不超过 question_max_length 个中文字符。
                7. questions 不要生成泛信息型问题,例如「做某事要注意什么风险」。

                输出 JSON 结构:
                {
                  "parent_category_share": "",
                  "industry_share": "",
                  "region_share": "",
                  "questions": ["", "", ""]
                }
                """;
    }
}

package com.huanjing.geo.module.presale.generate.llm;

/**
 * Page03「AI 搜索新战场」生成提示词。
 */
public final class MarketBattlegroundPromptTemplates {

    public static final String SYSTEM_INSTRUCTION = """
            你是售前诊断报告 Page03「AI 搜索新战场」的数据与文案生成助手。
            你必须只输出一个合法 JSON 对象,不得输出 Markdown、解释、代码块或额外文本。
            所有字段名和数组长度必须严格遵守用户提供的 JSON 模板。
            占比字段必须是 ^\\d+(\\.\\d{1,2})?%$ 格式的纯字符串,不得包含「约」、空格、范围、单位说明或其他文字。
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
                3. parent_category_name 为当前经营品类的上一级消费决策大类,用 2-6 个中文字符表达,如「医疗健康」「餐饮消费」「教育培训」「家居装修」「汽车服务」。根据 industry、user_demand、sample_prompts 推导,不得输出品牌名、区域名、竞品名,避免「生活服务」「本地服务」等泛化兜底。
                4. 三个占比字段的口径固定如下:
                   - parent_category_share:在全部 AI 搜索/问答流量中,上一级消费决策大类相关问题的估算占比。
                   - industry_share:在 parent_category_name 对应的大类 AI 搜索/问答流量中,当前 industry/经营品类相关问题的估算占比。
                   - region_share:在全国当前 industry/经营品类 AI 搜索/问答流量中,当前 region 相关问题的估算占比。
                5. 所有占比必须带 %,且必须是 ^\\d+(\\.\\d{1,2})?%$ 格式的纯字符串;不得包含「约」、空格、范围、单位说明或其他文字。大类和品类占比最多 1 位小数,区域占比最多 2 位小数。
                6. 因子要符合当前行业/品类/区域的常识,但不要输出任何具体研究机构、报告名、URL 或发布日期。
                7. questions 必须是消费者决策型问题,必须包含 region,围绕当前经营品类或 sample_prompts 体现的品类。
                8. sample_prompts 只用于识别品类、场景和消费者表达方式;不要照抄其中的风险咨询/避坑/注意事项问题,而要改写为带选择、推荐、比较、购买决策意图的问题。
                9. questions 不得包含 brand_name,不得出现竞品名,单条总长度不超过 question_max_length 个字符,长度计算包含所有中文、英文、数字、标点和空格。
                10. questions 不要生成泛信息型问题,例如「做某事要注意什么风险」。

                输出 JSON 结构:
                {
                  "parent_category_name": "",
                  "parent_category_share": "",
                  "industry_share": "",
                  "region_share": "",
                  "questions": ["", "", ""]
                }
                """;
    }
}

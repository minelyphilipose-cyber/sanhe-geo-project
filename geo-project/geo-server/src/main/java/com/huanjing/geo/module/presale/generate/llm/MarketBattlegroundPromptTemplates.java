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
                请根据「报告录入信息」生成 Page03「AI 搜索新战场」JSON。

                报告录入信息:
                """ + inputJson + """

                总原则:
                1. 只输出 JSON 对象,不要输出 Markdown。
                2. 凡是 JSON 模板中已经预填值的字段,必须原样保留,不得改写;仅替换值为 "" 的字段。
                3. 不得新增字段,不得删除字段,不得改变字段名。
                4. 不得引用具体研究机构名、报告名、URL 或精确发布日期;来源统一使用「行业公开数据综合估算」或「公开口径综合测算」。
                5. 所有内容必须围绕当前客户的行业、品类、区域和品牌生成;如果行业 key 较抽象,请优先从 user_demand 和 sample_prompts 推断真实经营品类。

                固定文案规则:
                - topbar_title 固定为 "MARKET BATTLEGROUND · AI 搜索新战场"
                - topbar_right 固定为 "GEO · CONFIDENTIAL"
                - page_kicker 固定为 "THE NEW BATTLEGROUND FOR YOUR BRAND"
                - bridge_text 固定为 "↓ 聚焦到您的核心市场"
                - market_card.platform_label 固定为 "TOP 平台"
                - market_card.platform_suffix 固定为 "元宝 / Kimi 等"
                - narrative.brand_line_prefix 固定为 "→"
                - narrative.brand_name 使用 brand_name
                - footer_brand 使用 brand_name

                page_title 规则:
                - 固定句式为「每天，{决策主题}决策正在 AI 上发生」
                - {决策主题} 必须从当前客户真实经营品类中提炼,例如火锅、医美、口腔、家装、教培
                - 中文,12-22 字,必须包含 "AI",必须包含数量词或时间词,不要感叹号

                市场数据规则:
                - market_card.stats 必须 4 项,字段顺序为 value/unit/label,语义依次为:
                  1. AI 原生 APP 月活
                  2. 日均活跃用户
                  3. 日均提问总量
                  4. 豆包人均月使用
                - market_card.platforms 必须 3 项,字段只允许 name/value,平台依次为豆包、千问、DeepSeek。
                - platforms.value 是一个完整短文本,例如 "5.8亿月活",不要拆 unit 字段。

                流量因子规则:
                - 后端会按以下因子重新计算 national_card 和 regional_card 的总量,你必须把因子填在指定 row 中。
                - national_card.rows[0].value 必须是全网 AI 日均提问总量,必须带「亿次」或「万次」,例如 "10.5亿次"、"12.6亿次"、"15.2亿次"。
                - national_card.rows[1].value 必须是大类占比,必须带 %,最多 1 位小数,例如 "8.5%"、"12.5%"、"15.0%"。
                - national_card.rows[2].value 必须是当前品类在大类中的占比,必须带 %,最多 1 位小数,例如 "1.5%"、"2.5%"、"3.0%"。
                - regional_card.rows[1].value 必须是当前区域占比,必须带 %,最多 2 位小数,例如 "0.02%"、"0.05%"、"0.08%"、"0.20%"。
                - 因子数值要易计算,但不要全部使用小数点后为 0 的数;至少 1-2 个因子使用真实感小数,如 12.5%、1.5%、0.08%。
                - rows[3] 的 total 行仍需填写近似结果,但后端会按上述因子覆盖最终展示值。

                问题生成规则:
                - narrative.questions 必须 3 条。
                - 必须是消费者决策型问题,要包含 region,要围绕当前经营品类或 sample_prompts 中体现的品类。
                - 不得包含 brand_name,不得出现竞品名。
                - 不要生成泛信息型问题,例如「做某事要注意什么风险」。

                footnote 规则:
                - 必须包含三要素:综合估算口径、合理浮动区间、仅作量级参考且不构成精确市场断言。

                输出 JSON 结构:
                {
                  "topbar_title": "MARKET BATTLEGROUND · AI 搜索新战场",
                  "topbar_right": "GEO · CONFIDENTIAL",
                  "page_title": "",
                  "page_kicker": "THE NEW BATTLEGROUND FOR YOUR BRAND",
                  "market_card": {
                    "label": "",
                    "source": "来源：行业公开数据综合估算",
                    "stats": [
                      {"value": "", "unit": "", "label": "AI 原生 APP 月活"},
                      {"value": "", "unit": "", "label": "日均活跃用户"},
                      {"value": "", "unit": "", "label": "日均提问总量"},
                      {"value": "", "unit": "", "label": "豆包人均月使用"}
                    ],
                    "platform_label": "TOP 平台",
                    "platforms": [
                      {"name": "豆包", "value": ""},
                      {"name": "千问", "value": ""},
                      {"name": "DeepSeek", "value": ""}
                    ],
                    "platform_suffix": "元宝 / Kimi 等"
                  },
                  "national_card": {
                    "label": "",
                    "value_prefix": "",
                    "value": "",
                    "unit": "",
                    "subtitle": "",
                    "calculation_label": "",
                    "rows": [
                      {"label": "", "value": "", "is_total": false},
                      {"label": "", "value": "", "is_total": false},
                      {"label": "", "value": "", "is_total": false},
                      {"label": "", "value": "", "is_total": true}
                    ]
                  },
                  "bridge_text": "↓ 聚焦到您的核心市场",
                  "regional_card": {
                    "label": "",
                    "value_prefix": "",
                    "value": "",
                    "unit": "",
                    "subtitle": "",
                    "calculation_label": "",
                    "rows": [
                      {"label": "", "value": "", "is_total": false},
                      {"label": "", "value": "", "is_total": false},
                      {"label": "数据来源", "value": "公开口径综合测算", "is_total": false},
                      {"label": "", "value": "", "is_total": true}
                    ]
                  },
                  "narrative": {
                    "intro": "这意味着，消费者正在通过 AI 持续询问：",
                    "questions": ["", "", ""],
                    "conclusion": "而 AI 给出的答案，正在影响他们下一步选择。",
                    "brand_line_prefix": "→",
                    "brand_name": "",
                    "brand_line_suffix": "在这些场景中的真实可见度如何？详见下章诊断结果。"
                  },
                  "footnote": "",
                  "footer_brand": ""
                }
                """;
    }
}

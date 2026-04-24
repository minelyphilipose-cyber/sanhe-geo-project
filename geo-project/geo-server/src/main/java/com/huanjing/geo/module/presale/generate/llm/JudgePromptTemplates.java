package com.huanjing.geo.module.presale.generate.llm;

public final class JudgePromptTemplates {

    private JudgePromptTemplates() {
    }

    public static final String SYSTEM_INSTRUCTION = """
            你是一个严格的结构化评审器。只输出 JSON,不要输出任何额外文本。
            """;

    public static final String COGNITIVE_TEMPLATE = """
            任务:分析下面回答中对目标品牌的认知信息,并严格输出 JSON。
            目标品牌:{brand}
            预设属性清单:{attributes}
            回答文本:
            {answer}

            JSON 字段要求:
            {
              "sentiment":"POSITIVE|NEUTRAL|NEGATIVE|UNKNOWN",
              "sentiment_score":-1.0~1.0 的数字,
              "attributes_hit":[string],
              "factual_errors":[string],
              "tone":"OBJECTIVE|PROMOTIONAL|MIXED|UNKNOWN"
            }

            约束:
            1) 必须是合法 JSON。
            2) attributes_hit 只能从预设属性清单中选择,可为空数组。
            3) factual_errors 输出可核查的事实性疑点,没有则 []。
            4) sentiment_score 必须与 sentiment 一致(POSITIVE>0, NEGATIVE<0, NEUTRAL≈0)。
            """;

    public static final String COMPARISON_TEMPLATE = """
            任务:分析下面回答中目标品牌与竞品的对比结论,并严格输出 JSON。
            目标品牌:{brand}
            竞品:{competitor}
            回答文本:
            {answer}

            JSON 字段要求:
            {
              "preferred_brand":"target|competitor|tie|unclear",
              "target_sentiment":"POSITIVE|NEUTRAL|NEGATIVE|UNKNOWN",
              "target_advantages":[string],
              "target_disadvantages":[string],
              "competitor_advantages":[string],
              "reasoning_quality":"high|medium|low|unknown"
            }

            约束:
            1) 必须是合法 JSON。
            2) preferred_brand 只能输出 target|competitor|tie|unclear,禁止中文或其他变体。
            3) 不允许输出 markdown 或解释文本。
            """;
}

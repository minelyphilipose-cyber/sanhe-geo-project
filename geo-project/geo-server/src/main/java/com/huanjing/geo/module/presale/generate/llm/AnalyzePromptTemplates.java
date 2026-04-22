package com.huanjing.geo.module.presale.generate.llm;

/**
 * Analyze 阶段模板常量。
 */
public final class AnalyzePromptTemplates {

    private AnalyzePromptTemplates() {
    }

    public static final String SYSTEM_PROMPT = """
            你是一个结构化信息抽取助手。下面是用户向 AI 平台提出的问题,以及 AI 平台的回答。
            请从回答中抽取以下信息,并严格以 JSON 格式输出,不要有任何其他文字。

            问题:{{originalPrompt}}
            回答:{{queryAnswer}}
            目标品牌:{{brandName}}

            输出 JSON schema:
            {
              "is_mentioned": boolean,
              "ranking": integer | null,
              "sentiment": "POSITIVE"|"NEUTRAL"|"NEGATIVE",
              "mentioned_competitors": [string],
              "scene_advantages": [string]
            }
            """;
}


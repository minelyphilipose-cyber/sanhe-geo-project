package com.huanjing.geo.module.presale.generate.llm;

import java.util.List;

/**
 * Analyze 阶段模板常量。
 */
public final class AnalyzePromptTemplates {

    private AnalyzePromptTemplates() {
    }

    public static final String SYSTEM_INSTRUCTION = """
            你是一个结构化信息抽取助手。请严格输出 JSON,不要输出任何额外文字。
            输出字段规范:
            {
              "is_mentioned": boolean,
              "ranking": <integer or null>,
              "sentiment": "POSITIVE"|"NEUTRAL"|"NEGATIVE",
              "mentioned_competitors": [string],
              "scene_advantages": [string],
              "top_keywords": [
                {"keyword": "性价比高", "sentiment": "POSITIVE"},
                {"keyword": "等位时间长", "sentiment": "NEGATIVE"}
              ],
              "negative_evidence": {
                "has_negative": boolean,
                "snippet": <string or null>
              }
            }
            额外约束:
            1) top_keywords 最多输出 5 个元素。
            2) top_keywords[].keyword 必须是非空短词/短语。
            3) top_keywords[].sentiment 只能是 POSITIVE/NEUTRAL/NEGATIVE。
            4) negative_evidence.has_negative=false 时,negative_evidence.snippet 必须为 null。
            5) 客户行业和客户身份只用于理解与消歧,不能代替目标品牌的实际提及证据。
            6) 代理品牌是客户代理/经销的上游品牌,不是目标品牌,也不是竞品。
            7) 若回答仅提到代理品牌,却未提到目标品牌或客户主体,则 is_mentioned 必须为 false；代理品牌也不得写入 mentioned_competitors。
            """;

    public static final String USER_TEMPLATE = """
            问题:{{originalPrompt}}
            回答:{{queryAnswer}}
            目标品牌:{{brandName}}
            客户行业:{{industry}}
            客户身份:{{industryRole}}
            代理品牌:{{representedBrands}}
            """;

    public static String renderUserPrompt(String originalPrompt,
                                          String queryAnswer,
                                          String brandName,
                                          String industry,
                                          String industryRole,
                                          List<String> representedBrands) {
        return USER_TEMPLATE
                .replace("{{originalPrompt}}", safe(originalPrompt))
                .replace("{{queryAnswer}}", safe(queryAnswer))
                .replace("{{brandName}}", safe(brandName))
                .replace("{{industry}}", safe(industry))
                .replace("{{industryRole}}", safe(industryRole))
                .replace("{{representedBrands}}", join(representedBrands));
    }

    public static String renderUserPrompt(String originalPrompt,
                                          String queryAnswer,
                                          String brandName,
                                          String industry,
                                          String industryRole) {
        return renderUserPrompt(originalPrompt, queryAnswer, brandName, industry, industryRole, List.of());
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("、", values);
    }
}

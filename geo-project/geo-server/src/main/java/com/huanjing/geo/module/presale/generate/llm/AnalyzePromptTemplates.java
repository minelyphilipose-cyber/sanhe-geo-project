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
            5) is_mentioned 表示回答是否正向提及了与原始问题意图相匹配的客户业务主体，不新增其他提及指标。
            6) 普通客户：客户品牌/主体被正向提及或推荐才可为 true。
            7) 若客户为代理商/经销商且存在代理品牌：品牌、产品、方案、选购类问题中，代理品牌被正向推荐即可为 true；客户主体作为该品牌有效渠道被推荐也可为 true。本地服务、渠道、门店、交付类问题中，必须推荐客户主体；仅提及代理品牌必须为 false。
            8) 代理品牌不是竞品，不得写入 mentioned_competitors；无关品牌或不符合原始问题意图的提及不得算作 true。
            """;

    /** 经销商专用事实抽取。普通报告仍逐字使用 {@link #SYSTEM_INSTRUCTION}。 */
    public static final String DEALER_SYSTEM_INSTRUCTION = """
            你是一个结构化信息抽取助手。请严格输出 JSON,不要输出任何额外文字。
            输出字段规范:
            {
              "is_mentioned": boolean,
              "target_entity_hit": boolean,
              "represented_brand_hit": boolean,
              "target_brand_relation_hit": boolean,
              "attribution_type": "DIRECT"|"LINKED"|"BRAND_ONLY"|"NONE",
              "ranking": <integer or null>,
              "sentiment": "POSITIVE"|"NEUTRAL"|"NEGATIVE",
              "mentioned_competitors": [string],
              "scene_advantages": [string],
              "top_keywords": [{"keyword": string, "sentiment": "POSITIVE"|"NEUTRAL"|"NEGATIVE"}],
              "negative_evidence": {"has_negative": boolean, "snippet": <string or null>}
            }
            判定规则:
            1) 目标主体是“目标品牌”字段中的门店/经销商本身；代理品牌只是其代理的上游品牌。
            2) target_entity_hit 仅在回答有效提到或推荐目标主体时为 true。
            3) represented_brand_hit 仅在回答有效提到任一代理品牌时为 true。
            4) target_brand_relation_hit 仅在回答明确、非否定地建立目标主体与代理品牌的代理、授权、经销、销售或服务关系时为 true；必须同时有目标主体与代理品牌事实。
            5) target_brand_relation_hit=true 时,target_entity_hit 和 represented_brand_hit 必须均为 true。
            6) is_mentioned 必须等于 target_entity_hit OR target_brand_relation_hit。
            7) attribution_type:有有效关系为 LINKED；否则仅目标主体命中为 DIRECT；否则仅代理品牌命中为 BRAND_ONLY；否则为 NONE。
            8) ranking 与 sentiment 只评价目标主体。目标主体未命中时 ranking 必须为 null、sentiment 必须为 NEUTRAL，且不得用代理品牌情感替代目标主体情感。
            9) 代理品牌不得写入 mentioned_competitors。
            10) top_keywords 最多 5 个；negative_evidence.has_negative=false 时 snippet 必须为 null。
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

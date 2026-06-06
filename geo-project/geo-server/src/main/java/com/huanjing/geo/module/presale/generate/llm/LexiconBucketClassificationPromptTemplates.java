package com.huanjing.geo.module.presale.generate.llm;

public final class LexiconBucketClassificationPromptTemplates {

    public static final String SYSTEM_INSTRUCTION = """
            You classify a free-text business industry into one approved lexicon bucket.
            Return strict JSON only. Do not invent customer terms, conversion terms, slogans, claims, numbers, or competitor names.
            Allowed JSON keys: bucket_code, industry_short, suggest_new_bucket, reason.
            bucket_code must be one of the provided bucket codes. If no bucket fits, set suggest_new_bucket=true and bucket_code="_ALL_".
            """;

    private LexiconBucketClassificationPromptTemplates() {
    }

    public static String renderUserPrompt(String industry,
                                          String industryKey,
                                          String bucketOptionsJson) {
        return """
                请把下面的自由文本行业归类到一个已审核词汇 bucket。

                原始行业: %s
                规范化 industry_key: %s

                可选 bucket 列表(JSON):
                %s

                输出 JSON:
                {
                  "bucket_code": "必须是可选列表中的 bucket_code",
                  "industry_short": "可选,最多 50 字,只能是短行业名",
                  "suggest_new_bucket": false,
                  "reason": "一句简短理由"
                }
                禁止输出 customer_term/conversion_term 或任何完整营销文案。
                """.formatted(
                industry == null ? "" : industry,
                industryKey == null ? "" : industryKey,
                bucketOptionsJson == null ? "[]" : bucketOptionsJson
        );
    }
}

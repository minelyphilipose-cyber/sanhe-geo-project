package com.huanjing.geo.module.presale.dto.snapshot.raw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.huanjing.geo.module.presale.json.PresaleDateTimeJson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 情感明细(L1)。
 * <p>Schema v1.2 $.raw_snapshot.sentiment_detail</p>
 * <p>
 * 统计口径:两轮(275 + 55 = 330)合计。positive/neutral/negative_count 为 required,
 * top_keywords / negative_evidence 可选。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SentimentDetail {

    @JsonProperty("positive_count")
    private Integer positiveCount;

    @JsonProperty("neutral_count")
    private Integer neutralCount;

    @JsonProperty("negative_count")
    private Integer negativeCount;

    /** 高频情感关键词(词云数据)。 */
    @JsonProperty("top_keywords")
    private List<SentimentKeyword> topKeywords;

    /** 真实负面证据原文,用于 PDF 溯源展示。 */
    @JsonProperty("negative_evidence")
    private List<NegativeEvidence> negativeEvidence;

    /** 情感倾向枚举(稳定值,使用 Java enum 决策 3C)。 */
    public enum Sentiment {
        POSITIVE, NEUTRAL, NEGATIVE
    }

    /** 词云数据条目。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SentimentKeyword {
        private String keyword;
        private Integer frequency;
        private Sentiment sentiment;
        /** 词云字号(渲染用)。 */
        @JsonProperty("font_size")
        private Integer fontSize;
    }

    /** 真实负面证据单条。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NegativeEvidence {
        /** 证据所属情感。v1.3 起 negative_evidence 只保留 NEGATIVE。 */
        private Sentiment sentiment;
        @JsonProperty("platform_code")
        private String platformCode;
        @JsonProperty("platform_name")
        private String platformName;
        private String query;
        private String snippet;
        @JsonProperty("tested_at")
        @JsonSerialize(using = PresaleDateTimeJson.Serializer.class)
        @JsonDeserialize(using = PresaleDateTimeJson.Deserializer.class)
        private LocalDateTime testedAt;
    }
}

package com.huanjing.geo.module.presale.dto.snapshot.editable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 第 03 页「AI 搜索新战场」固定布局文案。
 * <p>布局固定,仅可编辑可见文案。字符串字段 null 表示未设置,normalizer 会补默认;空字符串表示用户明确清空。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarketBattleground {

    @JsonProperty("topbar_title")
    private String topbarTitle;

    @JsonProperty("topbar_right")
    private String topbarRight;

    @JsonProperty("page_title")
    private String pageTitle;

    @JsonProperty("page_kicker")
    private String pageKicker;

    @JsonProperty("market_card")
    private MarketCard marketCard;

    @JsonProperty("national_card")
    private CalculationCard nationalCard;

    @JsonProperty("bridge_text")
    private String bridgeText;

    @JsonProperty("regional_card")
    private CalculationCard regionalCard;

    private Narrative narrative;

    private String footnote;

    @JsonProperty("footer_brand")
    private String footerBrand;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MarketCard {
        private String label;
        private String source;
        private List<Stat> stats;
        @JsonProperty("platform_label")
        private String platformLabel;
        private List<Platform> platforms;
        @JsonProperty("platform_suffix")
        private String platformSuffix;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Stat {
        private String value;
        private String unit;
        private String label;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Platform {
        private String name;
        private String value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CalculationCard {
        private String label;
        @JsonProperty("value_prefix")
        private String valuePrefix;
        private String value;
        private String unit;
        private String subtitle;
        @JsonProperty("calculation_label")
        private String calculationLabel;
        private List<CalculationRow> rows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CalculationRow {
        private String label;
        private String value;
        @JsonProperty("is_total")
        private Boolean isTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Narrative {
        private String intro;
        private List<String> questions;
        private String conclusion;
        @JsonProperty("brand_line_prefix")
        private String brandLinePrefix;
        @JsonProperty("brand_name")
        private String brandName;
        @JsonProperty("brand_line_suffix")
        private String brandLineSuffix;
    }
}

package com.huanjing.geo.module.presale.dto.snapshot.computed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 推荐型高价值场景中的竞品压制事实源。
 *
 * <p>只描述"用户未点名品牌、AI 主动推荐"场景,不混入认知/对比裁判口径。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SceneCompetitorPressure {

    /** 推荐型高价值场景总数。 */
    @JsonProperty("hv_reco_total")
    private Integer hvRecoTotal;

    /** 满足压制口径的推荐型高价值场景数。 */
    @JsonProperty("suppressed_scene_count")
    private Integer suppressedSceneCount;

    /** 在压制场景中出现最多的竞品。 */
    @JsonProperty("top_suppressing_competitor")
    private String topSuppressingCompetitor;

    /** 逐推荐场景明细。 */
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        @JsonProperty("prompt_code")
        private String promptCode;
        private String query;
        private String intent;
        @JsonProperty("target_mentioned_platform_count")
        private Integer targetMentionedPlatformCount;
        @JsonProperty("platforms_evaluated")
        private Integer platformsEvaluated;
        private List<CompetitorPressure> competitors;
        private Boolean suppressed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompetitorPressure {
        private String name;
        @JsonProperty("mentioned_platform_count")
        private Integer mentionedPlatformCount;
    }
}

package com.huanjing.geo.module.presale.dto.snapshot.raw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top3 竞品条目(L1)。
 * <p>Schema v1.2 $.raw_snapshot.competitors[](maxItems=3)</p>
 * <p>
 * {@code scene_advantages_raw} 是 LLM 原始提取文本。
 * L3 {@code competitor_scene_descriptions[].scene_advantages_polished} 为 null 时前端回退此字段。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Competitor {

    /** 排名(1-3)。L3 的 competitor_rank 据此关联。 */
    private Integer rank;

    /** 竞品品牌名。 */
    private String name;

    /** 被提及次数(第一轮聚合)。 */
    @JsonProperty("mention_count")
    private Integer mentionCount;

    /** 被提及率(0-100)。 */
    @JsonProperty("mention_rate")
    private Double mentionRate;

    /** 平均排名,可 null。 */
    @JsonProperty("avg_ranking")
    private Double avgRanking;

    /** 竞品优势场景原始文本(LLM 提取)。 */
    @JsonProperty("scene_advantages_raw")
    private List<String> sceneAdvantagesRaw;
}

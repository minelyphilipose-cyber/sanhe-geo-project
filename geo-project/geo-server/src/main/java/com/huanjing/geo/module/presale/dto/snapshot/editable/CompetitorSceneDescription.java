package com.huanjing.geo.module.presale.dto.snapshot.editable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 竞品场景描述(L3)。
 * <p>Schema v1.2 $.editable_content.competitor_scene_descriptions[]</p>
 * <p>
 * 通过 {@link #competitorRank} 与 L1.competitors[rank-1] 对应(1 起)。
 * </p>
 * <p>
 * <b>回退规则:</b>scene_advantages_polished 为 null 时,merged view 回退到
 * L1.competitors[rank-1].scene_advantages_raw(LLM 原始提取文本)。
 * 这是 L3 唯一一处<b>向 L1 回退</b>的字段,其他 L3 字段都是向默认模板回退。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompetitorSceneDescription {

    /** 竞品排名 1-3,必填。 */
    @JsonProperty("competitor_rank")
    private Integer competitorRank;

    /**
     * 运营润色后的场景描述列表。
     * null 时前端回退到 L1.competitors[competitorRank-1].scene_advantages_raw。
     */
    @JsonProperty("scene_advantages_polished")
    private List<String> sceneAdvantagesPolished;
}

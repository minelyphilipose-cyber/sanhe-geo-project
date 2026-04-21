package com.huanjing.geo.module.presale.dto.snapshot.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 未覆盖的场景查询条目(附竞品覆盖情况)。
 * <p>Schema v1.2 $defs/sceneQueryMissing</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SceneQueryMissing {
    @JsonProperty("prompt_code")
    private String promptCode;
    @JsonProperty("prompt_content")
    private String promptContent;
    private String category;
    /** 此场景下覆盖的竞品名列表,用于前端展示"竞品都覆盖了此场景,我们缺席"。 */
    @JsonProperty("top_competitor_coverage")
    private List<String> topCompetitorCoverage;
}

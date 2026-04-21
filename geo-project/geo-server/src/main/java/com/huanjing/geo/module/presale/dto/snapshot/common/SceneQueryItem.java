package com.huanjing.geo.module.presale.dto.snapshot.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已覆盖的场景查询条目。
 * <p>Schema v1.2 $defs/sceneQueryItem</p>
 * <p>{@code category} 为中文字面值(推荐型/对比型/问题型/认知型/场景型),
 * 按决策 3C 保留 String 不做 enum。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SceneQueryItem {
    @JsonProperty("prompt_code")
    private String promptCode;
    @JsonProperty("prompt_content")
    private String promptContent;
    /** 意图分类(中文字面值,如"推荐型"/"场景型")。 */
    private String category;
}

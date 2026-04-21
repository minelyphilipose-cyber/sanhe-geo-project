package com.huanjing.geo.module.presale.dto.snapshot.computed;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本次综合评分(L2)。
 * <p>Schema v1.2 $.computed_snapshot.scores</p>
 * <p>
 * 五维分 overall/mention/ranking/sentiment/coverage 均 0-100。
 * {@code weights} 是本次使用的权重冻结副本,权重配置变更后派生新版本会写入不同的 weights。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Scores {

    private Double overall;
    private Double mention;
    private Double ranking;
    private Double sentiment;
    private Double coverage;

    /** 本次使用的权重配置(冻结)。 */
    private Weights weights;

    /** 评分权重。四个维度权重通常加和为 1.0。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Weights {
        private Double mention;
        private Double ranking;
        private Double sentiment;
        private Double coverage;
    }
}

package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;

import java.util.Set;

/** Single source of truth for prompt-result inclusion in report metrics. */
public record EffectiveSampleIndex(Set<ReuseDecisionService.ReuseKey> keys) {
    public EffectiveSampleIndex {
        keys = keys == null ? Set.of() : Set.copyOf(keys);
    }

    public boolean contains(PresaleAiPromptResult row, ReuseDecisionService reuseDecisionService) {
        if (row == null) return false;
        return keys.contains(reuseDecisionService.keyOf(row.getVersionId(), row.getBatchNo(),
                row.getPlatformCode(), row.getPromptTemplateId(), row.getCompetitorName()));
    }
}

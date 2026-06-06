package com.huanjing.geo.module.presale.generate.narrative;

import com.huanjing.geo.module.presale.dto.snapshot.computed.NarrativeProfile;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * L3 关键发现统一候选模型。
 *
 * <p>RULE / DERIVED / STRENGTH 三类候选必须先合并为本模型,再执行一次排序、
 * 去重和截断。排序时 primary archetype 命中优先于普通 priority。</p>
 */
@Value
@Builder
public class NarrativeFindingCandidate {
    NarrativeProfile.FindingSource source;
    String code;
    /**
     * 逻辑去重键。高价值覆盖不足和高价值场景缺失统一映射为 HV_COVERAGE_LOW。
     */
    String dedupeKey;
    NarrativeProfile.FindingTierLevel tier;
    int priority;
    NarrativeProfile.Archetype archetype;
    boolean primaryArchetypeMatch;
    String titleTemplateKey;
    String bodyTemplateKey;
    Map<String, Object> evidence;
}

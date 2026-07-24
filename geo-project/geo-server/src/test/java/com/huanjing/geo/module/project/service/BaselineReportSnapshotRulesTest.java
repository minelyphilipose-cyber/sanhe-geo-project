package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaselineReportSnapshotRulesTest {

    @Test
    void mapValueTier_freezesExistingQuestionTierAsReportValueTier() {
        assertThat(BaselineReportSnapshotRules.mapValueTier("A")).isEqualTo("HIGH");
        assertThat(BaselineReportSnapshotRules.mapValueTier("b")).isEqualTo("MID");
        assertThat(BaselineReportSnapshotRules.mapValueTier(" C ")).isEqualTo("LOW");
        assertThatThrownBy(() -> BaselineReportSnapshotRules.mapValueTier("D"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void normalizeValueTier_acceptsOnlyReportValueTierValues() {
        assertThat(BaselineReportSnapshotRules.normalizeValueTier("high")).isEqualTo("HIGH");
        assertThat(BaselineReportSnapshotRules.normalizeValueTier(" MID ")).isEqualTo("MID");
        assertThat(BaselineReportSnapshotRules.normalizeValueTier("LOW")).isEqualTo("LOW");
        assertThatThrownBy(() -> BaselineReportSnapshotRules.normalizeValueTier("A"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void classifyIntent_appliesRubricPriorityOrder() {
        assertThat(BaselineReportSnapshotRules.classifyIntent("装修公司 A 和 B 对比哪个好", null, "三合星链"))
                .isEqualTo(BaselineReportSnapshotRules.INTENT_COMPARISON);
        assertThat(BaselineReportSnapshotRules.classifyIntent("杭州哪家装修公司值得推荐", "scene", "三合星链"))
                .isEqualTo(BaselineReportSnapshotRules.INTENT_RECOMMENDATION);
        assertThat(BaselineReportSnapshotRules.classifyIntent("三合星链口碑怎么样", null, "三合星链"))
                .isEqualTo(BaselineReportSnapshotRules.INTENT_AWARENESS);
        assertThat(BaselineReportSnapshotRules.classifyIntent("预算 20 万准备做全屋定制应该注意什么", null, "三合星链"))
                .isEqualTo(BaselineReportSnapshotRules.INTENT_SCENE);
        assertThat(BaselineReportSnapshotRules.classifyIntent("装修合同有哪些风险", null, "三合星链"))
                .isEqualTo(BaselineReportSnapshotRules.INTENT_PROBLEM);
    }

    @Test
    void normalizeMentionType_definesScoreMentionEnum() {
        assertThat(BaselineReportSnapshotRules.normalizeMentionType(null)).isEqualTo("NONE");
        assertThat(BaselineReportSnapshotRules.normalizeMentionType("brand_alias")).isEqualTo("BRAND_ALIAS");
        assertThatThrownBy(() -> BaselineReportSnapshotRules.normalizeMentionType("FUZZY"))
                .isInstanceOf(BizException.class);
    }
}

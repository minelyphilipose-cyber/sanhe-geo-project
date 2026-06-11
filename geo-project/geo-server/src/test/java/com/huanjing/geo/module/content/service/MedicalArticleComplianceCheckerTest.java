package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.entity.MedicalComplianceHitLog;
import com.huanjing.geo.module.content.entity.MedicalComplianceRule;
import com.huanjing.geo.module.content.mapper.MedicalComplianceHitLogMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceRuleMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicalArticleComplianceCheckerTest {

    private MedicalComplianceRuleMapper ruleMapper;
    private MedicalComplianceHitLogMapper hitLogMapper;
    private MedicalArticleComplianceChecker checker;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(MedicalComplianceRuleMapper.class);
        hitLogMapper = mock(MedicalComplianceHitLogMapper.class);
        checker = new MedicalArticleComplianceChecker(ruleMapper, hitLogMapper, new ObjectMapper());
    }

    @Test
    void blocksConfiguredForbiddenPhraseAndWritesHitLog() {
        MedicalComplianceRule rule = new MedicalComplianceRule();
        rule.setId(7L);
        rule.setRuleType("absolute_claim");
        rule.setPattern("根治");
        rule.setMatchMode("contains");
        rule.setSeverity("block");
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        MedicalArticleComplianceChecker.CheckInput input = input("标题", "这个项目可以根治问题，适合所有人。", false, 2);
        MedicalArticleComplianceChecker.CheckResult result = checker.check(input);

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("absolute_claim");

        checker.logHits(input, result, 99L, "discard");
        ArgumentCaptor<MedicalComplianceHitLog> captor = ArgumentCaptor.forClass(MedicalComplianceHitLog.class);
        verify(hitLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getArticleId()).isEqualTo(99L);
        assertThat(captor.getValue().getRuleId()).isEqualTo(7L);
        assertThat(captor.getValue().getAction()).isEqualTo("discard");
    }

    @Test
    void highRiskChannelRequiresRiskAndRationalHints() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("医美项目怎么选", "选择前需要了解基本原理和正规机构资质。", true, 2)
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("risk_disclosure_missing", "rational_decision_missing");
    }

    @Test
    void blocksWhenBrandExposureExceedsTierLimit() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("星链口腔科普", "星链口腔提醒先评估风险，星链口腔建议理性权衡。", false, 1)
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("brand_exposure_exceeded");
    }

    private MedicalArticleComplianceChecker.CheckInput input(String title,
                                                            String content,
                                                            boolean highRisk,
                                                            int brandExposureLimit) {
        Brand brand = new Brand();
        brand.setId(3L);
        brand.setBrandName("星链口腔");
        MedicalArticleGenerationService.MedicalPromptContext context =
                new MedicalArticleGenerationService.MedicalPromptContext(
                        MedicalArticleConstants.INDUSTRY_ORAL,
                        MedicalArticleConstants.TIER_EDUCATION,
                        "implant",
                        "种植牙",
                        11L,
                        "种植牙术前评估通常关注哪些条件",
                        "medical_decision",
                        "risk",
                        "kernel",
                        brandExposureLimit,
                        false,
                        "style",
                        highRisk,
                        "qualification",
                        "license",
                        "scope",
                        null
                );
        return new MedicalArticleComplianceChecker.CheckInput(
                1L,
                2L,
                4L,
                3L,
                "self_media",
                "xiaohongshu",
                title,
                content,
                brand,
                context
        );
    }
}

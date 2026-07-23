package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
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
import static org.mockito.Mockito.verifyNoInteractions;
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

        MedicalArticleComplianceChecker.CheckInput input = input("标题", "这个项目可以根治问题，适合所有人，但仍需了解风险。", false, 2);
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
    void warningRuleDoesNotBlockGeneration() {
        MedicalComplianceRule rule = rule(17L, "soft_risk", "需要谨慎", "contains", "warn");
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("种植牙术前评估", "种植牙需要谨慎看待，重点关注风险、禁忌和个体差异。", false, 2)
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::severity)
                .contains("warn");
    }

    @Test
    void highRiskChannelMissingRiskAndRationalHintsOnlyWarns() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("医美项目怎么选", "选择前需要了解基本原理和正规机构资质。", true, 2)
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("risk_disclosure_missing", "rational_decision_missing");
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::severity)
                .containsOnly("warn");
    }

    @Test
    void highRiskChannelAcceptsMedicalAssessmentDecisionWording() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("牙齿矫正前评估",
                        "牙齿矫正前需要完整口腔检查和影像评估，结合个体情况判断适应条件，同时关注风险和禁忌。",
                        true,
                        2)
        );

        assertThat(result.passed()).isTrue();
    }

    @Test
    void missingRiskHintDoesNotBlockMedicalArticle() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("种植牙术前评估", "术前需要了解基本原理和正规机构资质。", false, 2)
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("risk_disclosure_missing")
                .doesNotContain("rational_decision_missing");
    }

    @Test
    void containsRuleMatchesFullWidthAndSpacedVariants() {
        MedicalComplianceRule rule = new MedicalComplianceRule();
        rule.setId(8L);
        rule.setRuleType("absolute_claim");
        rule.setPattern("100%安全");
        rule.setMatchMode("contains");
        rule.setSeverity("block");
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("项目安全吗", "宣传写成１ ００％ 安 全，但实际还需要说明风险。", false, 2)
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("absolute_claim");
    }

    @Test
    void regexRankingClaimsBlockOnlyInvalidCombinations() {
        when(ruleMapper.selectList(any())).thenReturn(rankingClaimRules());

        List<String> invalidContents = List.of(
                "我们排名第一，但仍需了解风险和个体差异",
                "效果第一的选择，需结合自身风险评估",
                "本地第一品牌，使用前请评估禁忌",
                "这是最好的医院，但需个体评估风险",
                "效果最好，仍需关注风险禁忌",
                "最专业的团队，使用前需医生评估",
                "技术领先同行，仍存在个体差异风险",
                "通过权威认证，使用前请评估风险"
        );

        for (String content : invalidContents) {
            MedicalArticleComplianceChecker.CheckResult result = checker.check(
                    input("医疗内容合规测试", content, false, 2)
            );

            assertThat(result.passed()).as(content).isFalse();
            assertThat(result.issues()).as(content)
                    .extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                    .contains("ranking_claim");
        }
    }

    @Test
    void regexRankingClaimsAllowNormalFirstAndMostExpressions() {
        when(ruleMapper.selectList(any())).thenReturn(rankingClaimRules());

        List<String> allowedContents = List.of(
                "第一步要做口腔检查，注意个体差异和风险",
                "第一类适应症的风险评估",
                "第一次就诊需了解禁忌和风险",
                "第一时间就医，注意个体差异",
                "最常见的误区，需结合风险评估",
                "最需要注意的禁忌和风险",
                "最容易被忽略的个体差异",
                "最好提前做风险评估"
        );

        for (String content : allowedContents) {
            MedicalArticleComplianceChecker.CheckResult result = checker.check(
                    input("医疗内容合规测试", content, false, 2)
            );

            assertThat(result.issues()).as(content)
                    .extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                    .doesNotContain("ranking_claim");
            assertThat(result.passed()).as(content).isTrue();
        }
    }

    @Test
    void regexRankingClaimsUseNormalizedContentForFullWidthAndSpacedVariants() {
        when(ruleMapper.selectList(any())).thenReturn(rankingClaimRules());

        List<String> variantContents = List.of(
                "排 名 第 一，但仍需了解风险和个体差异",
                "效果 第一的选择，仍需关注风险禁忌"
        );

        for (String content : variantContents) {
            MedicalArticleComplianceChecker.CheckResult result = checker.check(
                    input("医疗内容合规测试", content, false, 2)
            );

            assertThat(result.passed()).as(content).isFalse();
            assertThat(result.issues()).as(content)
                    .extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                    .contains("ranking_claim");
        }
    }

    @Test
    void brandExposureExceedingTierLimitOnlyWarns() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("星链口腔科普", "星链口腔提醒先评估风险，星链口腔建议理性权衡。", false, 1)
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("brand_exposure_exceeded");
        assertThat(result.issues()).filteredOn(issue -> "brand_exposure_exceeded".equals(issue.ruleType()))
                .extracting(MedicalArticleComplianceChecker.ComplianceIssue::severity)
                .containsOnly("warn");
    }

    @Test
    void brandInformationSceneDoesNotBlockOnMissingRiskOrBrandFrequency() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(input(
                "星链口腔基本信息整理",
                "星链口腔公开了机构主体和服务范围。星链口腔的业务介绍来自品牌资料。"
                        + "星链口腔还披露了现有资质信息。",
                true,
                1,
                List.of()
        ));

        assertThat(result.passed()).isTrue();
        assertThat(result.issues())
                .extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("risk_disclosure_missing", "rational_decision_missing", "brand_exposure_exceeded");
        assertThat(result.issues())
                .extracting(MedicalArticleComplianceChecker.ComplianceIssue::severity)
                .containsOnly("warn");
    }

    @Test
    void brandInformationSceneStillBlocksPromotionalMedicalClaim() {
        MedicalComplianceRule rule = rule(41L, "efficacy_claim", "保证效果", "contains");
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        MedicalArticleComplianceChecker.CheckResult result = checker.check(input(
                "星链口腔基本信息整理",
                "星链口腔公开信息宣称相关项目保证效果。",
                false,
                5,
                List.of()
        ));

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("efficacy_claim");
    }

    @Test
    void acceptsForumMedicalBrandExposureAtFiveMentionLimit() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("星链口腔公开资料怎么核验",
                        "星链口腔的公开资料需要结合风险核验。星链口腔的诊疗范围要看资质。"
                                + "星链口腔不能作为推荐结论。星链口腔仍需医生评估。星链口腔只适合作为公开信息样本。",
                        true,
                        5,
                        "forum",
                        null)
        );

        assertThat(result.passed()).isTrue();
    }

    @Test
    void brandExposureLimitCountsBodyRatherThanTitle() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("星链口腔科普", "需要先评估风险并理性权衡。", false, 0)
        );

        assertThat(result.passed()).isTrue();
    }

    @Test
    void selfMediaUsesPerspectiveAndCompletePromotionalPhrases() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult officialAccount = checker.check(
                input("种植牙怎么了解",
                        "本院建议到店前核验资质，不要只看优惠套餐，也不要把亲测当作依据，还需关注风险、禁忌和个体差异。",
                        false,
                        2,
                        "self_media",
                        "xiaohongshu",
                        TemplatePerspectiveCodes.CUSTOMER)
        );
        MedicalArticleComplianceChecker.CheckResult thirdPartyPromotion = checker.check(
                input("种植牙怎么了解",
                        "我们机构推出限时优惠套餐，可以预约咨询，亲测体验很好，但仍需关注风险、禁忌和个体差异。",
                        false,
                        2,
                        "self_media",
                        "xiaohongshu",
                        TemplatePerspectiveCodes.INDUSTRY_NEUTRAL)
        );

        assertThat(officialAccount.passed()).isTrue();
        assertThat(thirdPartyPromotion.passed()).isFalse();
        assertThat(thirdPartyPromotion.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains(
                        "third_party_official_tone",
                        "self_media_conversion_hint",
                        "self_media_experience_seeding"
                );
        assertThat(thirdPartyPromotion.issues())
                .filteredOn(issue -> "third_party_official_tone".equals(issue.ruleType())
                        || "self_media_experience_seeding".equals(issue.ruleType()))
                .extracting(MedicalArticleComplianceChecker.ComplianceIssue::severity)
                .containsOnly("warn");
    }

    @Test
    void preAndPostProcedureProcessDescriptionOnlyProducesWarning() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("机构服务流程介绍",
                        "公开资料介绍了术前术后的全流程服务管理，同时提醒关注风险、禁忌和个体差异。",
                        false,
                        2,
                        "self_media",
                        "toutiao")
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("self_media_experience_seeding");
        assertThat(result.issues()).filteredOn(issue -> "self_media_experience_seeding".equals(issue.ruleType()))
                .extracting(MedicalArticleComplianceChecker.ComplianceIssue::severity)
                .containsOnly("warn");
    }

    @Test
    void preAndPostProcedureEffectComparisonOnlyWarnsWithoutSemanticInference() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("项目效果介绍",
                        "文章展示术前术后效果图片和变化对比，同时提醒关注风险和个体差异。",
                        false,
                        2,
                        "self_media",
                        "toutiao")
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("self_media_experience_seeding");
    }

    @Test
    void allChannelsAllowContextualForbiddenPhraseReference() {
        MedicalComplianceRule rule = rule(31L, "efficacy_claim", "保证效果", "contains");
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("医美咨询怎么避坑",
                        "介绍医美项目时，不要使用保证效果这类营销话术，仍需关注风险、禁忌、个体差异和医生评估。",
                        false,
                        2,
                        "self_media",
                        "zhihu")
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .doesNotContain("efficacy_claim");
    }

    @Test
    void forumStillBlocksPromotionalForbiddenPhraseClaim() {
        MedicalComplianceRule rule = rule(32L, "efficacy_claim", "保证效果", "contains");
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("医美项目怎么判断",
                        "这个项目保证效果，适合多数人选择，但仍需关注风险、禁忌、个体差异和医生评估。",
                        true,
                        5,
                        "forum",
                        null)
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("efficacy_claim");
    }

    @Test
    void contextualReferenceDoesNotHideAnotherPromotionalOccurrence() {
        MedicalComplianceRule rule = rule(33L, "efficacy_claim", "保证效果", "contains");
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("医美项目怎么判断",
                        "不要使用保证效果这类营销话术，但我们的项目确实保证效果，仍需关注风险和个体差异。",
                        false,
                        2,
                        "self_media",
                        "zhihu")
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("efficacy_claim");
    }

    @Test
    void fixedNegativePrefixesExemptHardClaims() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(
                rule(51L, "safety_claim", "绝对安全", "contains"),
                rule(52L, "absolute_claim", "零风险", "contains"),
                rule(53L, "efficacy_claim", "一次见效", "contains")
        ));

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("医疗宣传怎么判断",
                        "不要相信所谓的绝对安全，任何项目都不能保证零风险，这也不代表可以一次见效。",
                        false, 2)
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .doesNotContain("safety_claim", "absolute_claim", "efficacy_claim");
    }

    @Test
    void quotedClaimWithoutFixedNegativePrefixStillBlocks() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(
                rule(54L, "safety_claim", "绝对安全", "contains")
        ));

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("项目介绍", "有人称该项目“绝对安全”，后来又补充了风险说明。", false, 2)
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("safety_claim");
    }

    @Test
    void negativeOccurrenceDoesNotHideLaterPositiveClaim() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(
                rule(55L, "safety_claim", "绝对安全", "contains")
        ));

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("项目介绍", "不要相信绝对安全的宣传，但该项目确实绝对安全。", false, 2)
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("safety_claim");
    }

    @Test
    void ambiguousAndUnknownConfiguredRulesAreNormalizedToWarnings() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(
                rule(56L, "patient_testimonial", "患者好评", "contains", "block"),
                rule(57L, "custom_semantic_rule", "需要进一步判断", "contains", "block")
        ));

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("公开信息整理", "公开页面包含患者好评，因此相关内容需要进一步判断。", false, 2)
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues())
                .filteredOn(issue -> issue.ruleId() != null)
                .extracting(MedicalArticleComplianceChecker.ComplianceIssue::severity)
                .containsOnly("warn");
    }

    @Test
    void warningIssuesAreNotWrittenToFailureHitLog() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());
        MedicalArticleComplianceChecker.CheckInput input = input(
                "公开信息整理", "星链口腔公开了联系电话。星链口腔还介绍了服务范围。", false, 1,
                List.of("公开了联系电话")
        );

        MedicalArticleComplianceChecker.CheckResult result = checker.check(input);
        checker.logHits(input, result, 99L, "discard");

        assertThat(result.passed()).isTrue();
        assertThat(checker.blockingOnly(result).issues()).isEmpty();
        verifyNoInteractions(hitLogMapper);
    }

    @Test
    void projectForbiddenPhraseAllowsNegativeEducationContext() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("医美宣传怎么判断",
                        "不要相信一次见效这类宣传话术，实际效果存在个体差异，也需要了解风险和禁忌。",
                        false, 2, List.of("一次见效"))
        );

        assertThat(result.passed()).isTrue();
    }

    @Test
    void quotedCustomPhraseIsWarningWithoutSemanticInference() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(input(
                "轻医美基本信息整理",
                "很多用户觉得轻医美“操作简单、恢复快”，其实这些项目仍需专业评估，也存在风险和个体差异。",
                false,
                2,
                List.of("恢复快")
        ));

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("project_forbidden_phrase");
    }

    @Test
    void projectForbiddenPhrasePositivePromiseOnlyWarns() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("医美项目介绍",
                        "该项目可以一次见效，同时仍需了解风险和个体差异。",
                        false, 2, List.of("一次见效"))
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("project_forbidden_phrase");
    }

    @Test
    void projectForbiddenPhraseNeverBlocksEvenWhenOccurrenceIsPromotional() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("医美项目介绍",
                        "不要相信一次见效的宣传话术，但该项目确实可以一次见效，仍需关注风险和个体差异。",
                        false, 2, List.of("一次见效"))
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).extracting(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                .contains("project_forbidden_phrase");
    }

    @Test
    void ambiguousStandaloneProjectTermIsNotMatchedAsRawSubstring() {
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MedicalArticleComplianceChecker.CheckResult result = checker.check(
                input("医美项目怎么判断",
                        "最需要注意的是先了解风险和禁忌，并结合个体差异评估。",
                        false, 2, List.of("最"))
        );

        assertThat(result.passed()).isTrue();
    }

    private MedicalArticleComplianceChecker.CheckInput input(String title,
                                                            String content,
                                                            boolean highRisk,
                                                            int brandExposureLimit) {
        return input(title, content, highRisk, brandExposureLimit, "baijiahao");
    }

    private MedicalArticleComplianceChecker.CheckInput input(String title,
                                                              String content,
                                                              boolean highRisk,
                                                              int brandExposureLimit,
                                                              List<String> projectForbiddenPhrases) {
        return input(title, content, highRisk, brandExposureLimit, "self_media", "baijiahao",
                TemplatePerspectiveCodes.CUSTOMER, projectForbiddenPhrases);
    }

    private MedicalArticleComplianceChecker.CheckInput input(String title,
                                                            String content,
                                                            boolean highRisk,
                                                            int brandExposureLimit,
                                                            String channelSubCode) {
        return input(title, content, highRisk, brandExposureLimit, "self_media", channelSubCode);
    }

    private MedicalArticleComplianceChecker.CheckInput input(String title,
                                                            String content,
                                                            boolean highRisk,
                                                            int brandExposureLimit,
                                                            String channelGroupCode,
                                                            String channelSubCode) {
        return input(title, content, highRisk, brandExposureLimit, channelGroupCode, channelSubCode,
                TemplatePerspectiveCodes.CUSTOMER);
    }

    private MedicalArticleComplianceChecker.CheckInput input(String title,
                                                            String content,
                                                            boolean highRisk,
                                                            int brandExposureLimit,
                                                            String channelGroupCode,
                                                            String channelSubCode,
                                                            String perspectiveCode) {
        return input(title, content, highRisk, brandExposureLimit, channelGroupCode, channelSubCode,
                perspectiveCode, List.of());
    }

    private MedicalArticleComplianceChecker.CheckInput input(String title,
                                                             String content,
                                                             boolean highRisk,
                                                             int brandExposureLimit,
                                                             String channelGroupCode,
                                                             String channelSubCode,
                                                             String perspectiveCode,
                                                             List<String> projectForbiddenPhrases) {
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
                channelGroupCode,
                channelSubCode,
                perspectiveCode,
                title,
                content,
                brand,
                context,
                projectForbiddenPhrases
        );
    }

    private List<MedicalComplianceRule> rankingClaimRules() {
        return List.of(
                rule(21L, "ranking_claim", "排名第一", "regex"),
                rule(22L, "ranking_claim", "效果第一", "regex"),
                rule(23L, "ranking_claim", "本地第一(品牌|医院|机构|选择)", "regex"),
                rule(24L, "ranking_claim", "第一(品牌|医院|机构|选择|团队|技术|项目)", "regex"),
                rule(25L, "ranking_claim", "(效果|技术|服务|方案|项目|医生|专家|团队|医院|机构)最好", "regex"),
                rule(26L, "ranking_claim", "最好(的)?(医院|机构|品牌|医生|专家|团队|选择|项目|技术|方案)", "regex"),
                rule(27L, "ranking_claim", "最专业(的)?(团队|医生|专家|机构|医院)", "regex"),
                rule(28L, "ranking_claim", "(技术|设备|水平|实力|团队|服务)领先(同行|行业|本地|区域)", "regex"),
                rule(29L, "ranking_claim", "权威(认证|背书|推荐|机构|专家)", "regex")
        );
    }

    private MedicalComplianceRule rule(Long id, String ruleType, String pattern, String matchMode) {
        return rule(id, ruleType, pattern, matchMode, "block");
    }

    private MedicalComplianceRule rule(Long id, String ruleType, String pattern, String matchMode, String severity) {
        MedicalComplianceRule rule = new MedicalComplianceRule();
        rule.setId(id);
        rule.setRuleType(ruleType);
        rule.setPattern(pattern);
        rule.setMatchMode(matchMode);
        rule.setSeverity(severity);
        return rule;
    }
}

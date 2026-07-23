package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleVO;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.TopicAngleCategoryVO;
import com.huanjing.geo.module.content.entity.MedicalComplianceRule;
import com.huanjing.geo.module.content.entity.MedicalTopicAngle;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationBatchMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.MedicalChannelStyleModuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceHitLogMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceKernelMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceRuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalGenerationHistoryMapper;
import com.huanjing.geo.module.content.mapper.MedicalTopicAngleMapper;
import com.huanjing.geo.module.content.mapper.SpecialIndustryProfileMapper;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicalArticleConfigServiceTest {

    @Test
    void listTopicAngleCategoriesDeduplicatesEnabledCategories() {
        MedicalTopicAngleMapper topicAngleMapper = mock(MedicalTopicAngleMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(topicAngleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                topicAngle("medical_beauty", "医美", "skin_laser", "皮肤光电"),
                topicAngle("medical_beauty", "医美", "skin_laser", "皮肤光电"),
                topicAngle("medical_beauty", "医美", "injection", "注射类项目")
        ));
        MedicalArticleConfigService service = newService(topicAngleMapper, currentUserService);

        List<TopicAngleCategoryVO> categories = service.listTopicAngleCategories("medical_beauty", true);

        assertThat(categories).hasSize(2);
        assertThat(categories.get(0).categoryCode()).isEqualTo("skin_laser");
        assertThat(categories.get(0).topicAngleCount()).isEqualTo(2L);
        assertThat(categories.get(1).categoryCode()).isEqualTo("injection");
        assertThat(categories.get(1).topicAngleCount()).isEqualTo(1L);
        verify(currentUserService).ensurePermission("project.read");
    }

    @Test
    void createRuleNormalizesAmbiguousAndUnknownTypesToWarning() {
        MedicalTopicAngleMapper topicAngleMapper = mock(MedicalTopicAngleMapper.class);
        MedicalComplianceRuleMapper ruleMapper = mock(MedicalComplianceRuleMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        MedicalArticleConfigService service = newService(topicAngleMapper, ruleMapper, currentUserService);

        ComplianceRuleVO patientRule = service.createRule(ruleRequest("patient_testimonial", "患者好评", "block"));
        ComplianceRuleVO unknownRule = service.createRule(ruleRequest("custom_semantic_rule", "需要判断", "block"));
        ComplianceRuleVO safetyRule = service.createRule(ruleRequest("safety_claim", "绝对安全", "block"));

        assertThat(patientRule.severity()).isEqualTo("warn");
        assertThat(unknownRule.severity()).isEqualTo("warn");
        assertThat(safetyRule.severity()).isEqualTo("block");
        ArgumentCaptor<MedicalComplianceRule> captor = ArgumentCaptor.forClass(MedicalComplianceRule.class);
        verify(ruleMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(MedicalComplianceRule::getSeverity)
                .containsExactly("warn", "warn", "block");
    }

    @Test
    void createRuleRejectsInvalidRegexBeforePersisting() {
        MedicalTopicAngleMapper topicAngleMapper = mock(MedicalTopicAngleMapper.class);
        MedicalComplianceRuleMapper ruleMapper = mock(MedicalComplianceRuleMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        MedicalArticleConfigService service = newService(topicAngleMapper, ruleMapper, currentUserService);
        ComplianceRuleSaveRequest request = new ComplianceRuleSaveRequest(
                "safety_claim", null, null, null, null, "[未闭合", "regex", "block", true, null
        );

        assertThatThrownBy(() -> service.createRule(request))
                .hasMessageContaining("规则正则表达式无效");
        verify(ruleMapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void ruleTypeCatalogExposesHardAndWarningDispositions() {
        MedicalArticleConfigService service = newService(
                mock(MedicalTopicAngleMapper.class),
                mock(MedicalComplianceRuleMapper.class),
                mock(CurrentUserService.class)
        );

        assertThat(service.ruleTypes())
                .anySatisfy(type -> {
                    assertThat(type.ruleType()).isEqualTo("safety_claim");
                    assertThat(type.disposition()).isEqualTo("hard_block");
                })
                .anySatisfy(type -> {
                    assertThat(type.ruleType()).isEqualTo("comparison_case");
                    assertThat(type.disposition()).isEqualTo("warning");
                });
    }

    private static ComplianceRuleSaveRequest ruleRequest(String ruleType, String pattern, String severity) {
        return new ComplianceRuleSaveRequest(
                ruleType, null, null, null, null, pattern, "contains", severity, true, null
        );
    }

    private static MedicalTopicAngle topicAngle(String industryCode,
                                                String industryName,
                                                String categoryCode,
                                                String categoryName) {
        MedicalTopicAngle row = new MedicalTopicAngle();
        row.setIndustryCode(industryCode);
        row.setIndustryName(industryName);
        row.setCategoryCode(categoryCode);
        row.setCategoryName(categoryName);
        row.setEnabled(true);
        return row;
    }

    private static MedicalArticleConfigService newService(MedicalTopicAngleMapper topicAngleMapper,
                                                          CurrentUserService currentUserService) {
        return newService(topicAngleMapper, mock(MedicalComplianceRuleMapper.class), currentUserService);
    }

    private static MedicalArticleConfigService newService(MedicalTopicAngleMapper topicAngleMapper,
                                                          MedicalComplianceRuleMapper ruleMapper,
                                                          CurrentUserService currentUserService) {
        return new MedicalArticleConfigService(
                mock(SpecialIndustryProfileMapper.class),
                topicAngleMapper,
                ruleMapper,
                mock(MedicalComplianceKernelMapper.class),
                mock(MedicalChannelStyleModuleMapper.class),
                mock(MedicalComplianceHitLogMapper.class),
                mock(MedicalGenerationHistoryMapper.class),
                mock(ProjectMapper.class),
                mock(BrandMapper.class),
                mock(ArticleDraftMapper.class),
                mock(BatchArticleGenerationBatchMapper.class),
                mock(BatchArticleGenerationTaskMapper.class),
                mock(MedicalArticleComplianceChecker.class),
                currentUserService,
                specialIndustryService(),
                mock(SysDictItemMapper.class)
        );
    }

    private static SpecialIndustryService specialIndustryService() {
        SpecialIndustryProfileMapper profileMapper = mock(SpecialIndustryProfileMapper.class);
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(profileMapper.selectList(any())).thenReturn(List.of());
        when(mapper.selectList(any())).thenReturn(List.of());
        return new SpecialIndustryService(profileMapper, mapper);
    }
}

package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.dto.MedicalArticleDtos.SpecialIndustryProfileSaveRequest;
import com.huanjing.geo.module.content.dto.MedicalArticleDtos.SpecialIndustryProfileVO;
import com.huanjing.geo.module.content.entity.SpecialIndustryProfile;
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
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpecialIndustryProfileConfigServiceTest {

    private SpecialIndustryProfileMapper profileMapper;
    private SysDictItemMapper sysDictItemMapper;
    private CurrentUserService currentUserService;
    private MedicalArticleConfigService service;

    @BeforeEach
    void setUp() {
        profileMapper = mock(SpecialIndustryProfileMapper.class);
        sysDictItemMapper = mock(SysDictItemMapper.class);
        currentUserService = mock(CurrentUserService.class);
        when(profileMapper.selectOne(any())).thenReturn(null);
        when(profileMapper.selectList(any())).thenReturn(List.of());
        when(sysDictItemMapper.selectList(any())).thenReturn(List.of());
        when(sysDictItemMapper.selectOne(any())).thenReturn(null);
        service = new MedicalArticleConfigService(
                profileMapper,
                mock(MedicalTopicAngleMapper.class),
                mock(MedicalComplianceRuleMapper.class),
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
                new SpecialIndustryService(profileMapper, sysDictItemMapper),
                sysDictItemMapper
        );
    }

    @Test
    void createProfileNormalizesCodeAndSyncsComplianceIndustryDict() {
        SpecialIndustryProfileVO vo = service.createProfile(new SpecialIndustryProfileSaveRequest(
                " Finance ",
                "金融",
                " FINANCE ",
                "理财,贷款",
                "[{\"key\":\"brandQualificationDescription\",\"label\":\"行业资质说明\",\"required\":true}]",
                "{\"requireProjectQualification\":true}",
                "{\"industryLabel\":\"金融行业\"}",
                true,
                30,
                "金融客户"
        ));

        ArgumentCaptor<SpecialIndustryProfile> profileCaptor = ArgumentCaptor.forClass(SpecialIndustryProfile.class);
        verify(profileMapper).insert(profileCaptor.capture());
        SpecialIndustryProfile profile = profileCaptor.getValue();
        assertThat(profile.getIndustryCode()).isEqualTo("finance");
        assertThat(profile.getIndustryName()).isEqualTo("金融");
        assertThat(profile.getRegulatoryDomain()).isEqualTo("finance");
        assertThat(profile.getKeywords()).isEqualTo("理财,贷款");
        assertThat(profile.getSortOrder()).isEqualTo(30);
        assertThat(vo.industryCode()).isEqualTo("finance");

        ArgumentCaptor<SysDictItem> dictCaptor = ArgumentCaptor.forClass(SysDictItem.class);
        verify(sysDictItemMapper).insert(dictCaptor.capture());
        SysDictItem dictItem = dictCaptor.getValue();
        assertThat(dictItem.getDictType()).isEqualTo("compliance_industry");
        assertThat(dictItem.getDictKey()).isEqualTo("finance");
        assertThat(dictItem.getDictValue()).isEqualTo("金融");
        assertThat(dictItem.getRemark()).isEqualTo("理财,贷款");
        verify(currentUserService).ensurePermission("content.prompt_template.manage");
    }

    @Test
    void updateProfileKeepsExistingDictItemInSync() {
        SpecialIndustryProfile existed = new SpecialIndustryProfile();
        existed.setId(12L);
        existed.setIndustryCode("education");
        existed.setIndustryName("教育");
        when(profileMapper.selectById(12L)).thenReturn(existed);

        SysDictItem dictItem = new SysDictItem();
        dictItem.setId(99L);
        dictItem.setDictType("compliance_industry");
        dictItem.setDictKey("education");
        when(sysDictItemMapper.selectOne(any())).thenReturn(dictItem);

        service.updateProfile(12L, new SpecialIndustryProfileSaveRequest(
                "education",
                "教育培训",
                "education",
                "升学,培训",
                null,
                null,
                null,
                false,
                40,
                "教育客户"
        ));

        verify(profileMapper).updateById(existed);
        assertThat(existed.getIndustryName()).isEqualTo("教育培训");
        assertThat(existed.getEnabled()).isFalse();
        assertThat(existed.getSortOrder()).isEqualTo(40);

        verify(sysDictItemMapper).updateById(dictItem);
        assertThat(dictItem.getDictValue()).isEqualTo("教育培训");
        assertThat(dictItem.getEnabled()).isFalse();
        assertThat(dictItem.getRemark()).isEqualTo("升学,培训");
    }

    @Test
    void createProfileGeneratesHiddenDefaultsWhenAdvancedJsonIsOmitted() {
        SpecialIndustryProfileVO vo = service.createProfile(new SpecialIndustryProfileSaveRequest(
                "legal",
                "法律",
                "legal",
                "律所,法律咨询",
                null,
                null,
                null,
                true,
                50,
                null
        ));

        ArgumentCaptor<SpecialIndustryProfile> profileCaptor = ArgumentCaptor.forClass(SpecialIndustryProfile.class);
        verify(profileMapper).insert(profileCaptor.capture());
        SpecialIndustryProfile profile = profileCaptor.getValue();
        assertThat(profile.getQualificationSchemaJson()).contains("brandQualificationDescription");
        assertThat(profile.getReadinessPolicyJson()).contains("requireBrandQualificationDescription");
        assertThat(profile.getPromptLabelsJson()).contains("法律行业").contains("审查/备案编号");
        assertThat(vo.qualificationSchemaJson()).contains("brandQualificationDescription");
        assertThat(vo.promptLabelsJson()).contains("法律行业");
    }
}

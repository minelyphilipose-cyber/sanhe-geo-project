package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.mapper.MedicalChannelStyleModuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceKernelMapper;
import com.huanjing.geo.module.content.mapper.MedicalGenerationHistoryMapper;
import com.huanjing.geo.module.content.mapper.MedicalTopicAngleMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandOffering;
import com.huanjing.geo.module.customer.mapper.BrandOfferingMapper;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MedicalArticleGenerationServiceTest {

    private BrandOfferingMapper brandOfferingMapper;
    private MedicalArticleGenerationService service;

    @BeforeEach
    void setUp() {
        brandOfferingMapper = mock(BrandOfferingMapper.class);
        service = new MedicalArticleGenerationService(
                brandOfferingMapper,
                mock(MedicalTopicAngleMapper.class),
                mock(MedicalComplianceKernelMapper.class),
                mock(MedicalChannelStyleModuleMapper.class),
                mock(MedicalGenerationHistoryMapper.class),
                new ObjectMapper(),
                new SpecialIndustryService()
        );
    }

    @Test
    void medicalBrandWithoutEnabledQualifiedOfferingIsBlockedBeforeTopicFallback() {
        when(brandOfferingMapper.selectList(any())).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.resolveContext(
                project(),
                oralBrand(),
                "self_media",
                "wechat",
                topicConfig("implant")
        ));

        assertThat(ex.getMessage()).contains("特殊行业项目未配置已启用的资质项目");
    }

    @Test
    void medicalBrandCannotGenerateForCategoryOutsideEnabledQualifiedOfferings() {
        BrandOffering orthodontics = new BrandOffering();
        orthodontics.setId(3L);
        orthodontics.setBrandId(20L);
        orthodontics.setStatus("active");
        orthodontics.setMedicalProjectEnabled(true);
        orthodontics.setMedicalIndustryCode(MedicalArticleConstants.INDUSTRY_ORAL);
        orthodontics.setMedicalCategoryCode("orthodontics");
        when(brandOfferingMapper.selectList(any())).thenReturn(List.of(orthodontics));

        BizException ex = assertThrows(BizException.class, () -> service.resolveContext(
                project(),
                oralBrand(),
                "self_media",
                "wechat",
                topicConfig("implant")
        ));

        assertThat(ex.getMessage()).contains("特殊行业品类不属于该品牌已启用资质项目");
    }

    @Test
    void authorityMediaUsesEducationTier() {
        assertThat(service.resolveChannelTier("authority_media", null))
                .isEqualTo(MedicalArticleConstants.TIER_EDUCATION);
        assertThat(service.resolveChannelTier("authority_media", "industry_media"))
                .isEqualTo(MedicalArticleConstants.TIER_EDUCATION);
    }

    private Project project() {
        Project project = new Project();
        project.setId(10L);
        project.setBrandId(20L);
        project.setStatus("active");
        return project;
    }

    private Brand oralBrand() {
        Brand brand = new Brand();
        brand.setId(20L);
        brand.setBrandName("星链口腔");
        brand.setComplianceIndustryCode(MedicalArticleConstants.INDUSTRY_ORAL);
        brand.setIndustry("口腔医疗");
        return brand;
    }

    private BatchArticleGenerateRequest.TopicConfig topicConfig(String categoryCode) {
        BatchArticleGenerateRequest.TopicConfig topicConfig = new BatchArticleGenerateRequest.TopicConfig();
        topicConfig.setMedicalIndustryCode(MedicalArticleConstants.INDUSTRY_ORAL);
        topicConfig.setMedicalCategoryCode(categoryCode);
        return topicConfig;
    }
}

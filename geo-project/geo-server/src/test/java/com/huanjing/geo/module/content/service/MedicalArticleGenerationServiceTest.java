package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.entity.MedicalChannelStyleModule;
import com.huanjing.geo.module.content.entity.MedicalComplianceKernel;
import com.huanjing.geo.module.content.entity.MedicalTopicAngle;
import com.huanjing.geo.module.content.entity.SpecialIndustryProfile;
import com.huanjing.geo.module.content.mapper.MedicalChannelStyleModuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceKernelMapper;
import com.huanjing.geo.module.content.mapper.MedicalGenerationHistoryMapper;
import com.huanjing.geo.module.content.mapper.MedicalTopicAngleMapper;
import com.huanjing.geo.module.content.mapper.SpecialIndustryProfileMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandOffering;
import com.huanjing.geo.module.customer.mapper.BrandOfferingMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
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
    private MedicalTopicAngleMapper topicAngleMapper;
    private MedicalComplianceKernelMapper kernelMapper;
    private MedicalChannelStyleModuleMapper channelStyleMapper;
    private SpecialIndustryProfileMapper profileMapper;
    private SysDictItemMapper sysDictItemMapper;
    private MedicalArticleGenerationService service;

    @BeforeEach
    void setUp() {
        brandOfferingMapper = mock(BrandOfferingMapper.class);
        topicAngleMapper = mock(MedicalTopicAngleMapper.class);
        kernelMapper = mock(MedicalComplianceKernelMapper.class);
        channelStyleMapper = mock(MedicalChannelStyleModuleMapper.class);
        profileMapper = mock(SpecialIndustryProfileMapper.class);
        sysDictItemMapper = mock(SysDictItemMapper.class);
        when(profileMapper.selectList(any())).thenReturn(List.of());
        when(sysDictItemMapper.selectList(any())).thenReturn(List.of());
        service = new MedicalArticleGenerationService(
                brandOfferingMapper,
                topicAngleMapper,
                kernelMapper,
                channelStyleMapper,
                mock(MedicalGenerationHistoryMapper.class),
                new ObjectMapper(),
                specialIndustryService()
        );
    }

    private SpecialIndustryService specialIndustryService() {
        return new SpecialIndustryService(profileMapper, sysDictItemMapper);
    }

    @Test
    void medicalBrandWithoutEnabledQualifiedOfferingCanUseIndustryTopicLibrary() {
        when(brandOfferingMapper.selectList(any())).thenReturn(List.of());
        when(topicAngleMapper.selectList(any())).thenReturn(List.of(oralTopicAngle()));
        when(kernelMapper.selectOne(any())).thenReturn(kernel());
        when(channelStyleMapper.selectOne(any())).thenReturn(style());

        MedicalArticleGenerationService.MedicalPromptContext context = service.resolveContext(
                project(),
                oralBrand(),
                "self_media",
                "wechat",
                topicConfig("implant")
        ).orElseThrow();

        assertThat(context.categoryCode()).isEqualTo("implant");
        assertThat(context.qualificationRef()).isNull();
        assertThat(context.requireManualPublishReview()).isFalse();
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

    @Test
    void customSpecialIndustryCanResolveContextWithGenericQualificationSnapshot() {
        when(profileMapper.selectList(any())).thenReturn(List.of(profile("finance", "金融", "finance")));
        when(brandOfferingMapper.selectList(any())).thenReturn(List.of(financeOffering()));
        when(topicAngleMapper.selectList(any())).thenReturn(List.of(topicAngle()));
        when(kernelMapper.selectOne(any())).thenReturn(kernel());
        when(channelStyleMapper.selectOne(any())).thenReturn(style());

        MedicalArticleGenerationService.MedicalPromptContext context = service.resolveContext(
                project(),
                financeBrand(),
                "self_media",
                "wechat",
                financeTopicConfig("wealth_consulting")
        ).orElseThrow();

        assertThat(context.industryCode()).isEqualTo("finance");
        assertThat(context.categoryCode()).isEqualTo("wealth_consulting");
        assertThat(context.medicalLicense()).isEqualTo("持牌金融信息服务资质");
        assertThat(context.diagnosisScope()).isEqualTo("理财咨询与金融信息服务");
        assertThat(context.medicalAdReviewNo()).isNull();
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

    private Brand financeBrand() {
        Brand brand = new Brand();
        brand.setId(20L);
        brand.setBrandName("星链金融");
        brand.setComplianceIndustryCode("finance");
        brand.setIndustry("金融服务");
        brand.setBrandQualificationDescription("持牌金融信息服务资质");
        brand.setMainBusiness("理财咨询与金融信息服务");
        return brand;
    }

    private BatchArticleGenerateRequest.TopicConfig topicConfig(String categoryCode) {
        BatchArticleGenerateRequest.TopicConfig topicConfig = new BatchArticleGenerateRequest.TopicConfig();
        topicConfig.setMedicalIndustryCode(MedicalArticleConstants.INDUSTRY_ORAL);
        topicConfig.setMedicalCategoryCode(categoryCode);
        return topicConfig;
    }

    private BatchArticleGenerateRequest.TopicConfig financeTopicConfig(String categoryCode) {
        BatchArticleGenerateRequest.TopicConfig topicConfig = new BatchArticleGenerateRequest.TopicConfig();
        topicConfig.setMedicalIndustryCode("finance");
        topicConfig.setMedicalCategoryCode(categoryCode);
        return topicConfig;
    }

    private BrandOffering financeOffering() {
        BrandOffering offering = new BrandOffering();
        offering.setId(5L);
        offering.setBrandId(20L);
        offering.setStatus("active");
        offering.setMedicalProjectEnabled(true);
        offering.setMedicalIndustryCode("finance");
        offering.setMedicalCategoryCode("wealth_consulting");
        offering.setMedicalCategoryName("理财咨询");
        offering.setQualificationRef("金融信息服务资质覆盖");
        return offering;
    }

    private MedicalTopicAngle topicAngle() {
        MedicalTopicAngle angle = new MedicalTopicAngle();
        angle.setId(100L);
        angle.setIndustryCode("finance");
        angle.setIndustryName("金融");
        angle.setCategoryCode("wealth_consulting");
        angle.setCategoryName("理财咨询");
        angle.setTopicAngle("理财咨询前需要确认哪些风险");
        angle.setRecommendedFocus("risk");
        angle.setEnabled(true);
        return angle;
    }

    private MedicalTopicAngle oralTopicAngle() {
        MedicalTopicAngle angle = new MedicalTopicAngle();
        angle.setId(101L);
        angle.setIndustryCode(MedicalArticleConstants.INDUSTRY_ORAL);
        angle.setIndustryName("口腔");
        angle.setCategoryCode("implant");
        angle.setCategoryName("种植牙");
        angle.setTopicAngle("种植牙选择前需要了解哪些信息");
        angle.setRecommendedFocus("risk");
        angle.setEnabled(true);
        return angle;
    }

    private MedicalComplianceKernel kernel() {
        MedicalComplianceKernel kernel = new MedicalComplianceKernel();
        kernel.setSystemPrompt("不得承诺收益");
        kernel.setBrandExposureLimit(1);
        kernel.setRequireManualPublishReview(false);
        return kernel;
    }

    private MedicalChannelStyleModule style() {
        MedicalChannelStyleModule style = new MedicalChannelStyleModule();
        style.setChannelTier(MedicalArticleConstants.TIER_EDUCATION);
        style.setStylePrompt("保持科普口吻");
        style.setHighRisk(false);
        return style;
    }

    private SpecialIndustryProfile profile(String code, String name, String domain) {
        SpecialIndustryProfile profile = new SpecialIndustryProfile();
        profile.setIndustryCode(code);
        profile.setIndustryName(name);
        profile.setKeywords(name);
        profile.setRegulatoryDomain(domain);
        profile.setEnabled(true);
        profile.setSortOrder(10);
        return profile;
    }
}

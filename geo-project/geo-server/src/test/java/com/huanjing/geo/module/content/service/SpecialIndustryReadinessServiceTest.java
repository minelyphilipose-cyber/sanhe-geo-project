package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.entity.SpecialIndustryProfile;
import com.huanjing.geo.module.content.mapper.MedicalChannelStyleModuleMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceKernelMapper;
import com.huanjing.geo.module.content.mapper.MedicalTopicAngleMapper;
import com.huanjing.geo.module.content.mapper.SpecialIndustryProfileMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandOffering;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandOfferingMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpecialIndustryReadinessServiceTest {

    private BrandMapper brandMapper;
    private BrandOfferingMapper brandOfferingMapper;
    private MedicalTopicAngleMapper topicAngleMapper;
    private MedicalComplianceKernelMapper kernelMapper;
    private MedicalChannelStyleModuleMapper channelStyleMapper;
    private ProjectChannelAllocationMapper channelAllocationMapper;
    private SpecialIndustryProfileMapper profileMapper;
    private SysDictItemMapper sysDictItemMapper;
    private SpecialIndustryReadinessService service;

    @BeforeEach
    void setUp() {
        brandMapper = mock(BrandMapper.class);
        brandOfferingMapper = mock(BrandOfferingMapper.class);
        topicAngleMapper = mock(MedicalTopicAngleMapper.class);
        kernelMapper = mock(MedicalComplianceKernelMapper.class);
        channelStyleMapper = mock(MedicalChannelStyleModuleMapper.class);
        channelAllocationMapper = mock(ProjectChannelAllocationMapper.class);
        profileMapper = mock(SpecialIndustryProfileMapper.class);
        sysDictItemMapper = mock(SysDictItemMapper.class);
        when(profileMapper.selectList(any())).thenReturn(List.of());
        when(sysDictItemMapper.selectList(any())).thenReturn(List.of());
        service = new SpecialIndustryReadinessService(
                brandMapper,
                brandOfferingMapper,
                topicAngleMapper,
                kernelMapper,
                channelStyleMapper,
                channelAllocationMapper,
                specialIndustryService()
        );
    }

    private SpecialIndustryService specialIndustryService() {
        return new SpecialIndustryService(profileMapper, sysDictItemMapper);
    }

    @Test
    void nonMedicalBrandSkipsActivationReadiness() {
        Brand brand = new Brand();
        brand.setId(20L);
        brand.setIndustry("全屋智能");
        when(brandMapper.selectById(20L)).thenReturn(brand);

        assertDoesNotThrow(() -> service.validateProjectActivation(project()));

        verify(brandOfferingMapper, never()).selectList(any());
    }

    @Test
    void medicalBrandMissingRequiredProfileBlocksActivation() {
        Brand brand = medicalBrand();
        brand.setMedicalLicense(null);
        when(brandMapper.selectById(20L)).thenReturn(brand);
        when(channelAllocationMapper.selectList(any())).thenReturn(List.of());
        when(brandOfferingMapper.selectList(any())).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.validateProjectActivation(project()));

        assertThat(ex.getMessage())
                .contains("SPECIAL_INDUSTRY_READINESS_FAILED")
                .contains("缺少医疗机构执业许可信息")
                .contains("缺少已启用的特殊行业资质项目");
    }

    @Test
    void readyMedicalBrandCanActivate() {
        when(brandMapper.selectById(20L)).thenReturn(medicalBrand());
        when(channelAllocationMapper.selectList(any())).thenReturn(List.of(officialSiteAllocation()));
        when(brandOfferingMapper.selectList(any())).thenReturn(List.of(activeOffering()));
        when(topicAngleMapper.selectCount(any())).thenReturn(1L);
        when(kernelMapper.selectCount(any())).thenReturn(1L);
        when(channelStyleMapper.selectCount(any())).thenReturn(1L);

        assertDoesNotThrow(() -> service.validateProjectActivation(project()));
    }

    @Test
    void customSpecialIndustryUsesGenericQualificationInsteadOfMedicalLicense() {
        when(profileMapper.selectList(any())).thenReturn(List.of(profile("finance", "金融", "finance")));
        Brand brand = financeBrand();
        brand.setMedicalLicense(null);
        brand.setDiagnosisScope(null);
        brand.setMedicalAdReviewNo(null);
        when(brandMapper.selectById(20L)).thenReturn(brand);
        when(channelAllocationMapper.selectList(any())).thenReturn(List.of(selfMediaAllocation()));
        when(brandOfferingMapper.selectList(any())).thenReturn(List.of(activeFinanceOffering()));
        when(topicAngleMapper.selectCount(any())).thenReturn(1L);
        when(kernelMapper.selectCount(any())).thenReturn(1L);
        when(channelStyleMapper.selectCount(any())).thenReturn(1L);

        assertDoesNotThrow(() -> service.validateProjectActivation(project()));
    }

    @Test
    void customSpecialIndustryMissingGenericQualificationBlocksActivation() {
        when(profileMapper.selectList(any())).thenReturn(List.of(profile("finance", "金融", "finance")));
        Brand brand = financeBrand();
        brand.setBrandQualificationDescription(null);
        when(brandMapper.selectById(20L)).thenReturn(brand);
        when(channelAllocationMapper.selectList(any())).thenReturn(List.of());
        when(brandOfferingMapper.selectList(any())).thenReturn(List.of(activeFinanceOffering()));

        BizException ex = assertThrows(BizException.class, () -> service.validateProjectActivation(project()));

        assertThat(ex.getMessage())
                .contains("SPECIAL_INDUSTRY_READINESS_FAILED")
                .contains("缺少特殊行业资质说明")
                .doesNotContain("缺少医疗机构执业许可信息")
                .doesNotContain("缺少诊疗科目范围");
    }

    private Project project() {
        Project project = new Project();
        project.setId(10L);
        project.setBrandId(20L);
        project.setCompanyId(30L);
        return project;
    }

    private Brand medicalBrand() {
        Brand brand = new Brand();
        brand.setId(20L);
        brand.setBrandName("星链口腔");
        brand.setIndustry("口腔医疗");
        brand.setComplianceIndustryCode(MedicalArticleConstants.INDUSTRY_ORAL);
        brand.setMedicalLicense("PDY123456");
        brand.setDiagnosisScope("口腔科");
        brand.setMedicalAdReviewNo("皖医广审字2026第001号");
        return brand;
    }

    private Brand financeBrand() {
        Brand brand = new Brand();
        brand.setId(20L);
        brand.setBrandName("星链金融");
        brand.setIndustry("金融服务");
        brand.setComplianceIndustryCode("finance");
        brand.setBrandQualificationDescription("持牌金融信息服务资质");
        brand.setMainBusiness("理财咨询与金融信息服务");
        return brand;
    }

    private BrandOffering activeOffering() {
        BrandOffering offering = new BrandOffering();
        offering.setId(1L);
        offering.setBrandId(20L);
        offering.setOfferingName("种植牙");
        offering.setStatus("active");
        offering.setMedicalProjectEnabled(true);
        offering.setMedicalIndustryCode(MedicalArticleConstants.INDUSTRY_ORAL);
        offering.setMedicalCategoryCode("implant");
        offering.setMedicalCategoryName("种植牙");
        offering.setQualificationRef("诊疗科目含口腔种植专业");
        return offering;
    }

    private BrandOffering activeFinanceOffering() {
        BrandOffering offering = new BrandOffering();
        offering.setId(2L);
        offering.setBrandId(20L);
        offering.setOfferingName("理财咨询");
        offering.setStatus("active");
        offering.setMedicalProjectEnabled(true);
        offering.setMedicalIndustryCode("finance");
        offering.setMedicalCategoryCode("wealth_consulting");
        offering.setMedicalCategoryName("理财咨询");
        offering.setQualificationRef("金融信息服务资质覆盖");
        return offering;
    }

    private ProjectChannelAllocation officialSiteAllocation() {
        ProjectChannelAllocation allocation = new ProjectChannelAllocation();
        allocation.setProjectId(10L);
        allocation.setCompanyId(30L);
        allocation.setChannelCode("official_site");
        allocation.setAllocatedCount(2);
        return allocation;
    }

    private ProjectChannelAllocation selfMediaAllocation() {
        ProjectChannelAllocation allocation = new ProjectChannelAllocation();
        allocation.setProjectId(10L);
        allocation.setCompanyId(30L);
        allocation.setChannelCode("self_media:wechat");
        allocation.setAllocatedCount(2);
        return allocation;
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

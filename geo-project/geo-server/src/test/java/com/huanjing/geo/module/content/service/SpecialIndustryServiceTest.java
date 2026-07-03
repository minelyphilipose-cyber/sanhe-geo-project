package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.entity.SpecialIndustryProfile;
import com.huanjing.geo.module.content.mapper.SpecialIndustryProfileMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpecialIndustryServiceTest {

    private final SpecialIndustryProfileMapper profileMapper = mock(SpecialIndustryProfileMapper.class);
    private final SysDictItemMapper sysDictItemMapper = mock(SysDictItemMapper.class);
    private final SpecialIndustryService service = new SpecialIndustryService(profileMapper, sysDictItemMapper);

    SpecialIndustryServiceTest() {
        when(profileMapper.selectList(any())).thenReturn(List.of());
        when(sysDictItemMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void configuredComplianceIndustryIsDetectedFirst() {
        Brand brand = new Brand();
        brand.setComplianceIndustryCode(MedicalArticleConstants.INDUSTRY_ORAL);
        brand.setIndustry("全屋智能");

        assertThat(service.detectSpecialIndustryCode(brand))
                .contains(MedicalArticleConstants.INDUSTRY_ORAL);
        assertThat(service.isSpecialIndustry(brand)).isTrue();
    }

    @Test
    void industryTextCanFallbackToMedicalBeauty() {
        assertThat(service.detectMedicalIndustryCode(null, "轻医美连锁机构"))
                .contains(MedicalArticleConstants.INDUSTRY_MEDICAL_BEAUTY);
    }

    @Test
    void normalIndustryIsNotSpecialIndustry() {
        Brand brand = new Brand();
        brand.setIndustry("全屋智能");

        assertThat(service.detectSpecialIndustryCode(brand)).isEmpty();
        assertThat(service.isSpecialIndustry(brand)).isFalse();
    }

    @Test
    void customProfileIndustryCanBeDetectedByCodeAndKeywords() {
        when(profileMapper.selectList(any())).thenReturn(List.of(profile("finance", "金融", "金融,理财,贷款", "finance")));

        Brand brand = new Brand();
        brand.setComplianceIndustryCode("finance");
        brand.setIndustry("全屋智能");

        assertThat(service.detectSpecialIndustryCode(brand)).contains("finance");
        assertThat(service.detectSpecialIndustryCode(null, "互联网理财服务")).contains("finance");
        assertThat(service.detectMedicalIndustryCode(brand)).isEmpty();
        assertThat(service.isMedicalIndustry("finance")).isFalse();
        assertThat(service.industryLabel("finance")).isEqualTo("金融");
    }

    @Test
    void medicalProfileStillKeepsMedicalCompatibility() {
        when(profileMapper.selectList(any())).thenReturn(List.of(profile(MedicalArticleConstants.INDUSTRY_ORAL, "口腔医疗", "口腔,牙科", "medical")));

        Brand brand = new Brand();
        brand.setComplianceIndustryCode(MedicalArticleConstants.INDUSTRY_ORAL);

        assertThat(service.detectSpecialIndustryCode(brand)).contains(MedicalArticleConstants.INDUSTRY_ORAL);
        assertThat(service.detectMedicalIndustryCode(brand)).contains(MedicalArticleConstants.INDUSTRY_ORAL);
        assertThat(service.isMedicalIndustry(MedicalArticleConstants.INDUSTRY_ORAL)).isTrue();
    }

    private SpecialIndustryProfile profile(String code, String name, String keywords, String domain) {
        SpecialIndustryProfile profile = new SpecialIndustryProfile();
        profile.setIndustryCode(code);
        profile.setIndustryName(name);
        profile.setKeywords(keywords);
        profile.setRegulatoryDomain(domain);
        profile.setEnabled(true);
        profile.setSortOrder(10);
        return profile;
    }
}

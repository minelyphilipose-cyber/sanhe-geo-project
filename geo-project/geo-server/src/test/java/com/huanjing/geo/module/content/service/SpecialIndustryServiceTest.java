package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.customer.entity.Brand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpecialIndustryServiceTest {

    private final SpecialIndustryService service = new SpecialIndustryService();

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
}

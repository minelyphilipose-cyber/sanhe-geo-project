package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.customer.entity.Brand;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class SpecialIndustryService {

    private static final Set<String> MEDICAL_INDUSTRY_CODES = Set.of(
            MedicalArticleConstants.INDUSTRY_MEDICAL_BEAUTY,
            MedicalArticleConstants.INDUSTRY_ORAL
    );

    public boolean isSpecialIndustry(Brand brand) {
        return detectSpecialIndustryCode(brand).isPresent();
    }

    public boolean isSpecialIndustry(String complianceIndustryCode, String industryText) {
        return detectSpecialIndustryCode(complianceIndustryCode, industryText).isPresent();
    }

    public Optional<String> detectSpecialIndustryCode(Brand brand) {
        if (brand == null) {
            return Optional.empty();
        }
        return detectSpecialIndustryCode(brand.getComplianceIndustryCode(), brand.getIndustry());
    }

    public Optional<String> detectSpecialIndustryCode(String complianceIndustryCode, String industryText) {
        Optional<String> configured = normalizeSpecialIndustryCode(complianceIndustryCode);
        if (configured.isPresent()) {
            return configured;
        }
        return detectByIndustryText(industryText);
    }

    public Optional<String> detectMedicalIndustryCode(Brand brand) {
        return detectSpecialIndustryCode(brand).filter(this::isMedicalIndustry);
    }

    public Optional<String> detectMedicalIndustryCode(String complianceIndustryCode, String industryText) {
        return detectSpecialIndustryCode(complianceIndustryCode, industryText).filter(this::isMedicalIndustry);
    }

    public boolean isMedicalIndustry(String industryCode) {
        return normalizeCode(industryCode)
                .map(MEDICAL_INDUSTRY_CODES::contains)
                .orElse(false);
    }

    public String industryLabel(String industryCode) {
        return switch (normalizeCode(industryCode).orElse("")) {
            case MedicalArticleConstants.INDUSTRY_ORAL -> "口腔医疗";
            case MedicalArticleConstants.INDUSTRY_MEDICAL_BEAUTY -> "医美";
            default -> "特殊行业";
        };
    }

    private Optional<String> normalizeSpecialIndustryCode(String value) {
        return normalizeCode(value)
                .filter(MEDICAL_INDUSTRY_CODES::contains);
    }

    private Optional<String> detectByIndustryText(String industryText) {
        if (!StringUtils.hasText(industryText)) {
            return Optional.empty();
        }
        String value = industryText.trim().toLowerCase(Locale.ROOT);
        if (value.contains("医美") || value.contains("医疗美容") || value.contains("medical_beauty")) {
            return Optional.of(MedicalArticleConstants.INDUSTRY_MEDICAL_BEAUTY);
        }
        if (value.contains("口腔") || value.contains("牙科") || value.contains("oral")) {
            return Optional.of(MedicalArticleConstants.INDUSTRY_ORAL);
        }
        return Optional.empty();
    }

    private Optional<String> normalizeCode(String value) {
        return StringUtils.hasText(value) ? Optional.of(value.trim()) : Optional.empty();
    }
}

package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.entity.SpecialIndustryProfile;
import com.huanjing.geo.module.content.mapper.SpecialIndustryProfileMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SpecialIndustryService {

    private static final String DICT_TYPE_COMPLIANCE_INDUSTRY = "compliance_industry";
    private static final Set<String> MEDICAL_INDUSTRY_CODES = Set.of(
            MedicalArticleConstants.INDUSTRY_MEDICAL_BEAUTY,
            MedicalArticleConstants.INDUSTRY_ORAL
    );

    private final SpecialIndustryProfileMapper profileMapper;
    private final SysDictItemMapper sysDictItemMapper;

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
                .map(code -> profileByCode(code)
                        .map(SpecialIndustryConfig::isMedicalDomain)
                        .orElseGet(() -> MEDICAL_INDUSTRY_CODES.contains(code)))
                .orElse(false);
    }

    public String industryLabel(String industryCode) {
        String code = normalizeCode(industryCode).orElse("");
        return specialIndustryConfigs().stream()
                .filter(item -> item.code().equals(code))
                .map(SpecialIndustryConfig::label)
                .findFirst()
                .orElseGet(() -> legacyIndustryLabel(code));
    }

    public Optional<SpecialIndustryConfig> profileByCode(String industryCode) {
        Optional<String> code = normalizeCode(industryCode);
        if (code.isEmpty() || "none".equalsIgnoreCase(code.get())) {
            return Optional.empty();
        }
        return specialIndustryConfigs().stream()
                .filter(item -> item.code().equals(code.get()))
                .findFirst();
    }

    private Optional<String> normalizeSpecialIndustryCode(String value) {
        Optional<String> code = normalizeCode(value);
        if (code.isEmpty() || "none".equalsIgnoreCase(code.get())) {
            return Optional.empty();
        }
        String normalized = code.get().toLowerCase(Locale.ROOT);
        if (specialIndustryConfigs().stream().anyMatch(item -> item.code().equals(normalized))) {
            return Optional.of(normalized);
        }
        return MEDICAL_INDUSTRY_CODES.contains(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    private Optional<String> detectByIndustryText(String industryText) {
        if (!StringUtils.hasText(industryText)) {
            return Optional.empty();
        }
        String value = industryText.trim().toLowerCase(Locale.ROOT);
        return specialIndustryConfigs().stream()
                .filter(item -> item.matches(value))
                .map(SpecialIndustryConfig::code)
                .findFirst()
                .or(() -> detectByLegacyIndustryText(value));
    }

    private Optional<String> normalizeCode(String value) {
        return StringUtils.hasText(value) ? Optional.of(value.trim().toLowerCase(Locale.ROOT)) : Optional.empty();
    }

    private List<SpecialIndustryConfig> specialIndustryConfigs() {
        List<SpecialIndustryConfig> profiles = profileMapper.selectList(new LambdaQueryWrapper<SpecialIndustryProfile>()
                        .eq(SpecialIndustryProfile::getEnabled, true)
                        .orderByAsc(SpecialIndustryProfile::getSortOrder, SpecialIndustryProfile::getId))
                .stream()
                .map(this::toConfig)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (!profiles.isEmpty()) {
            return profiles;
        }
        List<SysDictItem> rows = sysDictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictType, DICT_TYPE_COMPLIANCE_INDUSTRY)
                .eq(SysDictItem::getEnabled, true)
                .orderByAsc(SysDictItem::getSortOrder, SysDictItem::getId));
        return rows.stream()
                .map(this::toConfig)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<SpecialIndustryConfig> toConfig(SpecialIndustryProfile profile) {
        Optional<String> code = normalizeCode(profile.getIndustryCode());
        if (code.isEmpty() || "none".equalsIgnoreCase(code.get())) {
            return Optional.empty();
        }
        String label = StringUtils.hasText(profile.getIndustryName()) ? profile.getIndustryName().trim() : code.get();
        List<String> terms = new ArrayList<>();
        terms.add(code.get());
        terms.add(label);
        if (StringUtils.hasText(profile.getKeywords())) {
            for (String term : profile.getKeywords().split("[,，;；、\\s]+")) {
                if (StringUtils.hasText(term)) {
                    terms.add(term.trim());
                }
            }
        }
        return Optional.of(new SpecialIndustryConfig(
                code.get(),
                label,
                normalizeDomain(profile.getRegulatoryDomain()),
                profile.getQualificationSchemaJson(),
                profile.getReadinessPolicyJson(),
                profile.getPromptLabelsJson(),
                terms
        ));
    }

    private Optional<SpecialIndustryConfig> toConfig(SysDictItem item) {
        Optional<String> code = normalizeCode(item.getDictKey());
        if (code.isEmpty() || "none".equalsIgnoreCase(code.get())) {
            return Optional.empty();
        }
        String label = StringUtils.hasText(item.getDictValue()) ? item.getDictValue().trim() : code.get();
        List<String> terms = new ArrayList<>();
        terms.add(code.get());
        terms.add(label);
        if (StringUtils.hasText(item.getRemark())) {
            for (String term : item.getRemark().split("[,，;；、\\s]+")) {
                if (StringUtils.hasText(term)) {
                    terms.add(term.trim());
                }
            }
        }
        return Optional.of(new SpecialIndustryConfig(
                code.get(),
                label,
                legacyDomain(code.get()),
                null,
                null,
                null,
                terms
        ));
    }

    private Optional<String> detectByLegacyIndustryText(String value) {
        if (value.contains("医美") || value.contains("医疗美容") || value.contains("medical_beauty")) {
            return Optional.of(MedicalArticleConstants.INDUSTRY_MEDICAL_BEAUTY);
        }
        if (value.contains("口腔") || value.contains("牙科") || value.contains("oral")) {
            return Optional.of(MedicalArticleConstants.INDUSTRY_ORAL);
        }
        return Optional.empty();
    }

    private String legacyIndustryLabel(String code) {
        return switch (code) {
            case MedicalArticleConstants.INDUSTRY_ORAL -> "口腔医疗";
            case MedicalArticleConstants.INDUSTRY_MEDICAL_BEAUTY -> "医美";
            default -> "特殊行业";
        };
    }

    private String legacyDomain(String code) {
        return MEDICAL_INDUSTRY_CODES.contains(code) ? "medical" : "custom";
    }

    private String normalizeDomain(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "custom";
    }

    public record SpecialIndustryConfig(String code,
                                        String label,
                                        String regulatoryDomain,
                                        String qualificationSchemaJson,
                                        String readinessPolicyJson,
                                        String promptLabelsJson,
                                        List<String> terms) {
        public boolean isMedicalDomain() {
            return "medical".equals(regulatoryDomain);
        }

        private boolean matches(String value) {
            for (String term : terms) {
                if (!StringUtils.hasText(term)) {
                    continue;
                }
                String normalized = term.trim().toLowerCase(Locale.ROOT);
                if (value.contains(normalized) || (value.length() >= 2 && normalized.contains(value))) {
                    return true;
                }
            }
            return false;
        }
    }
}

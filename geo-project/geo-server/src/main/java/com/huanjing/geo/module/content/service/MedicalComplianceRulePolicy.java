package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.dto.MedicalArticleDtos.ComplianceRuleTypeVO;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MedicalComplianceRulePolicy {

    static final String SEVERITY_BLOCK = "block";
    static final String SEVERITY_WARN = "warn";

    private static final Set<String> HARD_BLOCK_RULE_TYPES = Set.of(
            "absolute_claim",
            "efficacy_claim",
            "safety_claim",
            "oral_absolute",
            "promotion",
            "ranking_claim",
            "beauty_anxiety",
            "self_media_conversion_hint"
    );

    private static final Set<String> WARNING_RULE_TYPES = Set.of(
            "patient_testimonial",
            "comparison_case",
            "experience_seeding",
            "self_media_experience_seeding",
            "self_media_contact_reference",
            "brand_exposure_exceeded",
            "risk_disclosure_missing",
            "rational_decision_missing",
            "third_party_official_tone",
            "project_forbidden_phrase",
            "rule_configuration_error"
    );

    private static final Map<String, String> LEGACY_RULE_TYPES = Map.of(
            "safety_absolute", "safety_claim",
            "urgency_promotion", "promotion",
            "anxiety_inducement", "beauty_anxiety",
            "before_after", "comparison_case",
            "brand_exposure", "brand_exposure_exceeded",
            "rational_hint_missing", "rational_decision_missing"
    );

    private static final List<ComplianceRuleTypeVO> CATALOG = List.of(
            hard("absolute_claim", "绝对化承诺"),
            hard("efficacy_claim", "疗效或结果承诺"),
            hard("safety_claim", "绝对安全承诺"),
            hard("oral_absolute", "口腔效果或终身承诺"),
            hard("promotion", "促销活动"),
            hard("ranking_claim", "排名和最高级宣传"),
            hard("beauty_anxiety", "容貌焦虑和外貌贬损"),
            hard("self_media_conversion_hint", "明确咨询转化引导"),
            warning("patient_testimonial", "患者证明或案例倾向"),
            warning("comparison_case", "前后对比倾向"),
            warning("experience_seeding", "体验种草倾向"),
            warning("self_media_experience_seeding", "自媒体体验倾向"),
            warning("self_media_contact_reference", "中性联系方式或到店描述"),
            warning("brand_exposure_exceeded", "品牌露出超过建议值"),
            warning("risk_disclosure_missing", "缺少风险提示"),
            warning("rational_decision_missing", "缺少理性决策提示"),
            warning("third_party_official_tone", "第三方疑似官方口吻"),
            warning("project_forbidden_phrase", "项目或品牌自定义表达")
    );

    private MedicalComplianceRulePolicy() {
    }

    static ComplianceDisposition resolveDisposition(String ruleType, String configuredSeverity) {
        if (SEVERITY_WARN.equals(normalize(configuredSeverity))) {
            return ComplianceDisposition.WARNING;
        }
        return HARD_BLOCK_RULE_TYPES.contains(canonicalRuleType(ruleType))
                ? ComplianceDisposition.HARD_BLOCK
                : ComplianceDisposition.WARNING;
    }

    static String canonicalRuleType(String ruleType) {
        String normalized = normalize(ruleType);
        return LEGACY_RULE_TYPES.getOrDefault(normalized, normalized);
    }

    static boolean isKnownRuleType(String ruleType) {
        String canonical = canonicalRuleType(ruleType);
        return HARD_BLOCK_RULE_TYPES.contains(canonical) || WARNING_RULE_TYPES.contains(canonical);
    }

    static boolean canHardBlock(String ruleType) {
        return HARD_BLOCK_RULE_TYPES.contains(canonicalRuleType(ruleType));
    }

    static List<ComplianceRuleTypeVO> catalog() {
        return CATALOG;
    }

    static String effectiveSeverity(String ruleType, String configuredSeverity) {
        return resolveDisposition(ruleType, configuredSeverity) == ComplianceDisposition.HARD_BLOCK
                ? SEVERITY_BLOCK
                : SEVERITY_WARN;
    }

    static boolean isBlocking(String ruleType, String configuredSeverity) {
        return resolveDisposition(ruleType, configuredSeverity) == ComplianceDisposition.HARD_BLOCK;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static ComplianceRuleTypeVO hard(String code, String label) {
        return new ComplianceRuleTypeVO(code, label, "hard_block", List.of(SEVERITY_BLOCK, SEVERITY_WARN),
                List.of("contains", "regex"));
    }

    private static ComplianceRuleTypeVO warning(String code, String label) {
        return new ComplianceRuleTypeVO(code, label, "warning", List.of(SEVERITY_WARN),
                List.of("contains", "regex"));
    }

    enum ComplianceDisposition {
        HARD_BLOCK,
        WARNING
    }
}

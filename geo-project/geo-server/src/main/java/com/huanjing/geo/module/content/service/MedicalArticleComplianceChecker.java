package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.entity.MedicalComplianceHitLog;
import com.huanjing.geo.module.content.entity.MedicalComplianceRule;
import com.huanjing.geo.module.content.mapper.MedicalComplianceHitLogMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceRuleMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
@RequiredArgsConstructor
public class MedicalArticleComplianceChecker {

    private static final List<String> RISK_HINTS = List.of("风险", "禁忌", "不适合", "个体差异", "医生评估", "专业评估");
    private static final List<String> RATIONAL_HINTS = List.of(
            "理性", "不要盲目", "不建议盲目", "需结合", "结合自身", "结合具体", "先评估", "权衡",
            "医生评估", "专业评估", "综合判断", "个体情况", "个体差异", "适应条件", "完整检查", "口腔检查", "影像评估"
    );
    private static final List<String> THIRD_PARTY_OFFICIAL_TONE_HINTS = List.of(
            "我们机构", "本院", "我院", "本机构", "官方推荐", "官方指定", "官方建议"
    );
    private static final List<String> SELF_MEDIA_HARD_CONVERSION_HINTS = List.of(
            "预约咨询", "私信了解", "私信咨询", "点击咨询", "来院咨询", "加微信", "扫码咨询",
            "限时优惠", "优惠套餐", "限时名额", "到店优惠", "活动价", "免费体验"
    );
    private static final List<String> SELF_MEDIA_NEUTRAL_CONTACT_HINTS = List.of(
            "联系电话", "到店体验"
    );
    private static final List<String> SELF_MEDIA_EXPERIENCE_HINTS = List.of(
            "亲测", "我做过", "朋友做过", "真实案例", "前后对比", "术前术后"
    );
    private static final Pattern EXPLICIT_NEGATION_PREFIX = Pattern.compile(
            "(?:不要相信|切勿相信|不得宣称|不可宣称|无法保证|不能保证|不代表|并非|不是|不要|不得|不能|不应)"
                    + "[^，。！？；,.!?;\\n\\r]{0,8}$"
    );

    private final MedicalComplianceRuleMapper ruleMapper;
    private final MedicalComplianceHitLogMapper hitLogMapper;
    private final ObjectMapper objectMapper;

    public CheckResult check(CheckInput input) {
        if (input == null || input.medicalContext() == null) {
            return CheckResult.pass();
        }
        String content = normalize(input.title()) + "\n" + normalize(input.content());
        List<ComplianceIssue> issues = new ArrayList<>();
        collectProjectForbiddenPhraseIssues(input, content, issues);
        collectRuleIssues(input, content, issues);
        collectBuiltInIssues(input, content, issues);
        issues = deduplicateIssues(issues);
        return issues.stream().anyMatch(this::isBlocking)
                ? new CheckResult(false, issues)
                : new CheckResult(true, issues);
    }

    private List<ComplianceIssue> deduplicateIssues(List<ComplianceIssue> issues) {
        Map<String, ComplianceIssue> unique = new LinkedHashMap<>();
        for (ComplianceIssue issue : issues) {
            String key = MedicalComplianceRulePolicy.canonicalRuleType(issue.ruleType())
                    + "|" + normalizeForMatch(issue.matchedText())
                    + "|" + MedicalComplianceRulePolicy.effectiveSeverity(issue.ruleType(), issue.severity());
            unique.putIfAbsent(key, issue);
        }
        return List.copyOf(unique.values());
    }

    public String toJson(CheckResult result) {
        try {
            return objectMapper.writeValueAsString(result == null ? CheckResult.pass() : result);
        } catch (JsonProcessingException ex) {
            return "{\"passed\":false,\"issues\":[{\"ruleType\":\"serialize_error\",\"message\":\"compliance result json serialize failed\"}]}";
        }
    }

    public CheckResult blockingOnly(CheckResult result) {
        if (result == null || result.issues().isEmpty()) {
            return CheckResult.pass();
        }
        List<ComplianceIssue> blockingIssues = result.issues().stream()
                .filter(this::isBlocking)
                .toList();
        return new CheckResult(blockingIssues.isEmpty(), blockingIssues);
    }

    public void logHits(CheckInput input, CheckResult result, Long articleId, String action) {
        if (input == null || result == null || result.issues().isEmpty()) {
            return;
        }
        for (ComplianceIssue issue : result.issues().stream().filter(this::isBlocking).toList()) {
            MedicalComplianceHitLog row = new MedicalComplianceHitLog();
            row.setArticleId(articleId);
            row.setBatchId(input.batchId());
            row.setTaskId(input.taskId());
            row.setProjectId(input.projectId());
            row.setBrandId(input.brandId());
            row.setRuleId(issue.ruleId());
            row.setRuleType(issue.ruleType());
            row.setMatchedText(limit(issue.matchedText(), 255));
            row.setCheckStage("post_generate");
            row.setAction(StringUtils.hasText(action) ? action : "retry");
            hitLogMapper.insert(row);
        }
    }

    private void collectRuleIssues(CheckInput input, String content, List<ComplianceIssue> issues) {
        MedicalArticleGenerationService.MedicalPromptContext context = input.medicalContext();
        List<MedicalComplianceRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<MedicalComplianceRule>()
                .eq(MedicalComplianceRule::getEnabled, true)
                .and(wrapper -> wrapper
                        .isNull(MedicalComplianceRule::getIndustryCode)
                        .or()
                        .eq(MedicalComplianceRule::getIndustryCode, context.industryCode()))
                .and(wrapper -> wrapper
                        .isNull(MedicalComplianceRule::getChannelTier)
                        .or()
                        .eq(MedicalComplianceRule::getChannelTier, context.channelTier()))
                .and(wrapper -> wrapper
                        .isNull(MedicalComplianceRule::getChannelGroupCode)
                        .or()
                        .eq(MedicalComplianceRule::getChannelGroupCode, input.channelGroupCode()))
                .and(wrapper -> wrapper
                        .isNull(MedicalComplianceRule::getChannelSubCode)
                        .or()
                        .eq(MedicalComplianceRule::getChannelSubCode, input.channelSubCode())));
        for (MedicalComplianceRule rule : rules) {
            String pattern = normalize(rule.getPattern());
            String matchedText;
            try {
                matchedText = findFirstUnnegatedMatch(content, pattern, rule.getMatchMode());
            } catch (PatternSyntaxException ex) {
                issues.add(new ComplianceIssue(
                        rule.getId(),
                        "rule_configuration_error",
                        MedicalComplianceRulePolicy.SEVERITY_WARN,
                        pattern,
                        "规则正则表达式无效，已跳过且不阻断文章：" + ex.getDescription()
                ));
                continue;
            }
            if (!StringUtils.hasText(matchedText)) {
                continue;
            }
            String ruleType = MedicalComplianceRulePolicy.canonicalRuleType(rule.getRuleType());
            issues.add(new ComplianceIssue(
                    rule.getId(),
                    ruleType,
                    MedicalComplianceRulePolicy.effectiveSeverity(ruleType, rule.getSeverity()),
                    matchedText,
                    "命中特殊行业内容规则：" + ruleType
            ));
        }
    }

    private void collectProjectForbiddenPhraseIssues(CheckInput input,
                                                      String content,
                                                      List<ComplianceIssue> issues) {
        for (String phrase : input.projectForbiddenPhrases()) {
            String normalizedPhrase = normalize(phrase);
            if (!isPreciseProjectPhrase(normalizedPhrase)) {
                continue;
            }
            String matchedText = findFirstUnnegatedMatch(content, normalizedPhrase, "contains");
            if (!StringUtils.hasText(matchedText)) {
                continue;
            }
            issues.add(new ComplianceIssue(
                    null,
                    "project_forbidden_phrase",
                    MedicalComplianceRulePolicy.SEVERITY_WARN,
                    matchedText,
                    "命中特殊行业项目禁用表达，记录提醒但不阻断生成"
            ));
        }
    }

    private void collectBuiltInIssues(CheckInput input, String content, List<ComplianceIssue> issues) {
        MedicalArticleGenerationService.MedicalPromptContext context = input.medicalContext();
        if (lacksAny(content, RISK_HINTS)) {
            issues.add(new ComplianceIssue(null, "risk_disclosure_missing", MedicalComplianceRulePolicy.SEVERITY_WARN,
                    null, "内容未包含风险、禁忌或个体差异提示，记录提醒但不阻断生成"));
        }
        if (context.highRiskChannel() && lacksAny(content, RATIONAL_HINTS)) {
            issues.add(new ComplianceIssue(null, "rational_decision_missing", MedicalComplianceRulePolicy.SEVERITY_WARN,
                    null, "高风险渠道内容未包含理性决策提示，记录提醒但不阻断生成"));
        }
        Integer limit = context.brandExposureLimit();
        if (limit != null && limit >= 0 && input.brand() != null && StringUtils.hasText(input.brand().getBrandName())) {
            int count = countOccurrences(normalize(input.content()), input.brand().getBrandName().trim());
            if (count > limit) {
                issues.add(new ComplianceIssue(null, "brand_exposure_exceeded", MedicalComplianceRulePolicy.SEVERITY_WARN,
                        input.brand().getBrandName(),
                        "正文品牌露出次数超过渠道建议值，记录提醒但不阻断生成：" + count + "/" + limit));
            }
        }
        collectSelfMediaIssues(input, content, issues);
    }

    private void collectSelfMediaIssues(CheckInput input, String content, List<ComplianceIssue> issues) {
        if (!ArticlePromptChannels.SELF_MEDIA.equals(input.channelGroupCode())) {
            return;
        }
        if (TemplatePerspectiveCodes.isThirdParty(input.perspectiveCode())) {
            addFirstHitIssue(content, THIRD_PARTY_OFFICIAL_TONE_HINTS, issues,
                    "third_party_official_tone", MedicalComplianceRulePolicy.SEVERITY_WARN,
                    "特殊行业第三方账号内容疑似出现客户官方/机构身份口吻，记录提醒但不阻断生成");
        }
        addFirstHitIssue(content, SELF_MEDIA_HARD_CONVERSION_HINTS, issues,
                "self_media_conversion_hint", MedicalComplianceRulePolicy.SEVERITY_BLOCK,
                "特殊行业自媒体内容出现明确咨询、促销或其他直接转化引导");
        addFirstHitIssue(content, SELF_MEDIA_NEUTRAL_CONTACT_HINTS, issues,
                "self_media_contact_reference", MedicalComplianceRulePolicy.SEVERITY_WARN,
                "特殊行业自媒体内容出现中性联系方式或到店描述，记录提醒但不阻断生成");
        addFirstHitIssue(content, SELF_MEDIA_EXPERIENCE_HINTS, issues,
                "self_media_experience_seeding", MedicalComplianceRulePolicy.SEVERITY_WARN,
                "特殊行业自媒体内容出现体验、案例或前后表达，记录提醒但不阻断生成");
    }

    private void addFirstHitIssue(String content,
                                  List<String> hints,
                                  List<ComplianceIssue> issues,
                                  String ruleType,
                                  String severity,
                                  String message) {
        for (String hint : hints) {
            String matchedText = findFirstUnnegatedMatch(content, hint, "contains");
            if (StringUtils.hasText(matchedText)) {
                issues.add(new ComplianceIssue(null, ruleType,
                        MedicalComplianceRulePolicy.effectiveSeverity(ruleType, severity), matchedText, message));
                return;
            }
        }
    }

    private boolean lacksAny(String content, List<String> hints) {
        String normalizedContent = normalizeForMatch(content);
        for (String hint : hints) {
            if (normalizedContent.contains(normalizeForMatch(hint))) {
                return false;
            }
        }
        return true;
    }

    private String findFirstUnnegatedMatch(String content, String pattern, String matchMode) {
        if (!StringUtils.hasText(pattern)) {
            return null;
        }
        String normalizedContent = normalizeForMatch(content);
        if ("regex".equalsIgnoreCase(matchMode)) {
            java.util.regex.Matcher matcher = Pattern.compile(normalizeForMatch(pattern), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(normalizedContent);
            while (matcher.find()) {
                if (!isExplicitlyNegated(normalizedContent, matcher.start())) {
                    return matcher.group();
                }
            }
            return null;
        }
        String normalizedPattern = normalizeForMatch(pattern);
        if (normalizedPattern.codePointCount(0, normalizedPattern.length()) < 2) {
            return null;
        }
        int index = normalizedContent.indexOf(normalizedPattern);
        while (index >= 0) {
            if (!isExplicitlyNegated(normalizedContent, index)) {
                return pattern;
            }
            index = normalizedContent.indexOf(normalizedPattern, index + normalizedPattern.length());
        }
        return null;
    }

    private boolean isBlocking(ComplianceIssue issue) {
        return issue != null && MedicalComplianceRulePolicy.isBlocking(issue.ruleType(), issue.severity());
    }

    private boolean isExplicitlyNegated(String normalizedContent, int hitIndex) {
        int start = Math.max(0, hitIndex - 24);
        String prefix = normalizedContent.substring(start, hitIndex);
        return EXPLICIT_NEGATION_PREFIX.matcher(prefix).find();
    }

    private boolean isPreciseProjectPhrase(String phrase) {
        if (!ArticleForbiddenPhrasePolicy.isEffectivePhrase(phrase)) {
            return false;
        }
        String normalized = normalizeForMatch(phrase);
        return normalized.codePointCount(0, normalized.length()) >= 2;
    }

    private int countOccurrences(String content, String needle) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(needle)) {
            return 0;
        }
        int count = 0;
        int offset = 0;
        while ((offset = content.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeForMatch(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\n' || ch == '\r') {
                if (builder.length() == 0 || builder.charAt(builder.length() - 1) != '\n') {
                    builder.append('\n');
                }
                continue;
            }
            if (Character.isWhitespace(ch) || ch == '\u3000') {
                continue;
            }
            if (ch >= '\uFF01' && ch <= '\uFF5E') {
                ch = (char) (ch - 0xFEE0);
            }
            builder.append(Character.toLowerCase(ch));
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public record CheckInput(Long batchId,
                             Long taskId,
                             Long projectId,
                             Long brandId,
                             String channelGroupCode,
                             String channelSubCode,
                             String perspectiveCode,
                             String title,
                             String content,
                             Brand brand,
                             MedicalArticleGenerationService.MedicalPromptContext medicalContext,
                             List<String> projectForbiddenPhrases) {
        public CheckInput {
            projectForbiddenPhrases = projectForbiddenPhrases == null ? List.of() : List.copyOf(projectForbiddenPhrases);
        }

        public CheckInput(Long batchId,
                          Long taskId,
                          Long projectId,
                          Long brandId,
                          String channelGroupCode,
                          String channelSubCode,
                          String perspectiveCode,
                          String title,
                          String content,
                          Brand brand,
                          MedicalArticleGenerationService.MedicalPromptContext medicalContext) {
            this(batchId, taskId, projectId, brandId, channelGroupCode, channelSubCode, perspectiveCode,
                    title, content, brand, medicalContext, List.of());
        }
    }

    public record ComplianceIssue(Long ruleId,
                                  String ruleType,
                                  String severity,
                                  String matchedText,
                                  String message) {
    }

    public record CheckResult(boolean passed, List<ComplianceIssue> issues) {
        public CheckResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public static CheckResult pass() {
            return new CheckResult(true, List.of());
        }
    }
}

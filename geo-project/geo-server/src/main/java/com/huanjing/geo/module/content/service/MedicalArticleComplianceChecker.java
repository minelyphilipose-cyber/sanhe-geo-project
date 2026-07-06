package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.entity.MedicalComplianceHitLog;
import com.huanjing.geo.module.content.entity.MedicalComplianceRule;
import com.huanjing.geo.module.content.mapper.MedicalComplianceHitLogMapper;
import com.huanjing.geo.module.content.mapper.MedicalComplianceRuleMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final List<String> PERSONAL_ACCOUNT_OFFICIAL_TONE_HINTS = List.of(
            "我们机构", "本院", "我院", "本机构", "官方推荐", "官方指定", "官方建议"
    );
    private static final List<String> PERSONAL_ACCOUNT_CONVERSION_HINTS = List.of(
            "预约咨询", "私信了解", "私信咨询", "点击咨询", "到店", "来院", "联系电话", "加微信", "扫码",
            "优惠", "套餐", "名额", "限时", "活动价"
    );
    private static final List<String> PERSONAL_ACCOUNT_EXPERIENCE_HINTS = List.of(
            "亲测", "我做过", "朋友做过", "真实案例", "前后对比", "术前术后"
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
        collectRuleIssues(input, content, issues);
        collectBuiltInIssues(input, content, issues);
        return issues.stream().anyMatch(this::isBlocking)
                ? new CheckResult(false, issues)
                : new CheckResult(true, issues);
    }

    public String toJson(CheckResult result) {
        try {
            return objectMapper.writeValueAsString(result == null ? CheckResult.pass() : result);
        } catch (JsonProcessingException ex) {
            return "{\"passed\":false,\"issues\":[{\"ruleType\":\"serialize_error\",\"message\":\"compliance result json serialize failed\"}]}";
        }
    }

    public void logHits(CheckInput input, CheckResult result, Long articleId, String action) {
        if (input == null || result == null || result.issues().isEmpty()) {
            return;
        }
        for (ComplianceIssue issue : result.issues()) {
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
            String matchedText = findMatchedText(content, pattern, rule.getMatchMode());
            if (!StringUtils.hasText(matchedText)) {
                continue;
            }
            if (isForumContextualReference(input, content, matchedText)) {
                continue;
            }
            issues.add(new ComplianceIssue(
                    rule.getId(),
                    normalize(rule.getRuleType()),
                    normalize(rule.getSeverity()),
                    matchedText,
                    "命中医疗合规规则：" + normalize(rule.getRuleType())
            ));
        }
    }

    private void collectBuiltInIssues(CheckInput input, String content, List<ComplianceIssue> issues) {
        MedicalArticleGenerationService.MedicalPromptContext context = input.medicalContext();
        if (lacksAny(content, RISK_HINTS)) {
            issues.add(new ComplianceIssue(null, "risk_disclosure_missing", "block", null, "医疗内容缺少风险、禁忌或个体差异提示"));
        }
        if (context.highRiskChannel() && lacksAny(content, RATIONAL_HINTS)) {
            issues.add(new ComplianceIssue(null, "rational_decision_missing", "block", null, "高风险渠道内容缺少理性决策提示"));
        }
        Integer limit = context.brandExposureLimit();
        if (limit != null && limit >= 0 && input.brand() != null && StringUtils.hasText(input.brand().getBrandName())) {
            int count = countOccurrences(normalize(input.content()), input.brand().getBrandName().trim());
            if (count > limit) {
                issues.add(new ComplianceIssue(null, "brand_exposure_exceeded", "block", input.brand().getBrandName(),
                        "正文品牌露出次数超过医疗渠道档位限制：" + count + "/" + limit));
            }
        }
        collectPersonalSelfMediaIssues(input, content, issues);
    }

    private void collectPersonalSelfMediaIssues(CheckInput input, String content, List<ComplianceIssue> issues) {
        if (!ArticlePromptChannels.SELF_MEDIA.equals(input.channelGroupCode())
                || "baijiahao".equals(input.channelSubCode())) {
            return;
        }
        addFirstHitIssue(content, PERSONAL_ACCOUNT_OFFICIAL_TONE_HINTS, issues,
                "personal_account_official_tone", "特殊行业个人号内容出现官方/机构身份口吻");
        addFirstHitIssue(content, PERSONAL_ACCOUNT_CONVERSION_HINTS, issues,
                "personal_account_conversion_hint", "特殊行业个人号内容出现咨询、到店、优惠或其他转化引导");
        addFirstHitIssue(content, PERSONAL_ACCOUNT_EXPERIENCE_HINTS, issues,
                "personal_account_experience_seeding", "特殊行业个人号内容出现亲测、案例或前后对比表达");

        if (input.brand() == null || !StringUtils.hasText(input.brand().getBrandName())) {
            return;
        }
        String brandName = input.brand().getBrandName().trim();
        int count = countOccurrences(content, brandName);
        if (count < 1) {
            issues.add(new ComplianceIssue(null, "personal_account_brand_exposure_missing", "block", brandName,
                    "特殊行业个人号内容缺少品牌/机构名露出：0/1"));
        } else if (count > 2) {
            issues.add(new ComplianceIssue(null, "personal_account_brand_exposure_exceeded", "block", brandName,
                    "特殊行业个人号内容品牌/机构名露出超过限制：" + count + "/2"));
        }
    }

    private void addFirstHitIssue(String content,
                                  List<String> hints,
                                  List<ComplianceIssue> issues,
                                  String ruleType,
                                  String message) {
        String normalizedContent = normalizeForMatch(content);
        for (String hint : hints) {
            if (normalizedContent.contains(normalizeForMatch(hint))) {
                issues.add(new ComplianceIssue(null, ruleType, "block", hint, message));
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

    private String findMatchedText(String content, String pattern, String matchMode) {
        if (!StringUtils.hasText(pattern)) {
            return null;
        }
        if ("regex".equalsIgnoreCase(matchMode)) {
            try {
                java.util.regex.Matcher matcher = Pattern.compile(normalizeForMatch(pattern), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(normalizeForMatch(content));
                return matcher.find() ? matcher.group() : null;
            } catch (PatternSyntaxException ex) {
                return null;
            }
        }
        return normalizeForMatch(content).contains(normalizeForMatch(pattern)) ? pattern : null;
    }

    private boolean isBlocking(ComplianceIssue issue) {
        return issue != null && !"warn".equalsIgnoreCase(normalize(issue.severity()));
    }

    private boolean isForumContextualReference(CheckInput input, String content, String matchedText) {
        if (!ArticlePromptChannels.FORUM.equals(input.channelGroupCode()) || !StringUtils.hasText(matchedText)) {
            return false;
        }
        String normalizedContent = normalizeForMatch(content);
        String normalizedHit = normalizeForMatch(matchedText);
        if (!StringUtils.hasText(normalizedHit)) {
            return false;
        }
        int index = normalizedContent.indexOf(normalizedHit);
        while (index >= 0) {
            int start = Math.max(0, index - 24);
            int end = Math.min(normalizedContent.length(), index + normalizedHit.length() + 12);
            String window = normalizedContent.substring(start, end);
            if (containsAny(window, List.of(
                    "不得", "不要", "不能", "禁止", "避免", "不写", "不应", "不可", "别写", "避开",
                    "慎用", "违规", "不建议使用", "不要使用", "不能写成", "不要写成", "营销话术"
            ))) {
                return true;
            }
            index = normalizedContent.indexOf(normalizedHit, index + normalizedHit.length());
        }
        return false;
    }

    private boolean containsAny(String value, List<String> needles) {
        for (String needle : needles) {
            if (value.contains(normalizeForMatch(needle))) {
                return true;
            }
        }
        return false;
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
                             String title,
                             String content,
                             Brand brand,
                             MedicalArticleGenerationService.MedicalPromptContext medicalContext) {
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

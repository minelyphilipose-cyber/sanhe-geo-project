package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class XiaohongshuArticleStructureValidator {

    private static final int STANDARD_BODY_MIN_CHARS = 600;
    private static final int STANDARD_BODY_MAX_CHARS = 900;
    private static final int NEUTRAL_BODY_MIN_CHARS = 500;
    private static final int NEUTRAL_BODY_MAX_CHARS = 700;
    private static final int STANDARD_MAX_H2 = 3;
    private static final int NEUTRAL_MAX_H2 = 2;
    private static final int STANDARD_MAX_LIST_ITEMS = 6;
    private static final int NEUTRAL_MAX_LIST_ITEMS = 4;

    private static final Pattern H2 = Pattern.compile("(?m)^##\\s+\\S.*$");
    private static final Pattern DEEP_HEADING = Pattern.compile("(?m)^#{3,}\\s+\\S.*$");
    private static final Pattern LIST_ITEM = Pattern.compile("(?m)^\\s*(?:[-*+]\\s+|\\d+[.)、]\\s*)\\S.*$");
    private static final Pattern TOPIC_TAG = Pattern.compile("#[^#\\s]{1,30}#");
    private static final Pattern EMOJI = Pattern.compile("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}]");
    private static final Pattern NEUTRAL_TITLE_PUNCTUATION = Pattern.compile("[！？!?【】\\[\\]（）()《》<>|｜/\\\\]");

    private static final List<String> STANDARD_TITLE_RISK_WORDS = List.of(
            "强推", "种草", "闭眼选", "必看", "宝藏", "天花板", "亲测", "实测", "性价比之王"
    );
    private static final List<String> NEUTRAL_TITLE_RISK_WORDS = List.of(
            "推荐", "种草", "避雷", "怎么选", "如何选", "哪家好", "必看", "必做", "亲测", "实测",
            "效果", "变美", "逆龄", "恢复快", "安全", "无痛", "零风险", "内幕", "揭秘", "真相",
            "踩坑", "后悔", "前后对比", "首选", "最佳", "最好", "排行榜", "机构选择", "医院选择"
    );
    private static final List<String> GENERIC_HEADINGS = List.of(
            "前言", "背景", "总结", "结语", "写在最后", "注意事项", "避雷指南", "选择指南"
    );

    public List<Violation> validate(String markdown,
                                    Project project,
                                    Brand brand,
                                    boolean neutralEducationMode) {
        List<Violation> violations = new ArrayList<>();
        String content = markdown == null ? "" : markdown.trim();
        String title = extractTitle(content);
        String body = extractBody(content);
        int titleMax = neutralEducationMode ? 20 : 28;
        int titleLength = title.codePointCount(0, title.length());
        if (titleLength == 0 || titleLength > titleMax) {
            violations.add(violation("xiaohongshu_title_length",
                    "小红书标题必须为1～" + titleMax + "个字，当前为" + titleLength + "个字"));
        }

        List<String> titleRiskWords = neutralEducationMode
                ? NEUTRAL_TITLE_RISK_WORDS
                : STANDARD_TITLE_RISK_WORDS;
        String titleRiskWord = firstContained(title, titleRiskWords);
        if (StringUtils.hasText(titleRiskWord)) {
            violations.add(violation("xiaohongshu_title_risk",
                    "小红书标题包含高风险表达：" + titleRiskWord));
        }
        if (neutralEducationMode && NEUTRAL_TITLE_PUNCTUATION.matcher(title).find()) {
            violations.add(violation("xiaohongshu_title_decoration",
                    "特殊行业中立科普标题只能使用无装饰的中性陈述短句"));
        }
        if (neutralEducationMode) {
            String brandReference = firstBrandReference(title + "\n" + body, project, brand);
            if (StringUtils.hasText(brandReference)) {
                violations.add(violation("xiaohongshu_neutral_brand_reference",
                        "特殊行业中立科普不得出现品牌或企业信息：" + brandReference));
            }
        }

        int bodyLength = plainTextLength(body);
        int bodyMin = neutralEducationMode ? NEUTRAL_BODY_MIN_CHARS : STANDARD_BODY_MIN_CHARS;
        int bodyMax = neutralEducationMode ? NEUTRAL_BODY_MAX_CHARS : STANDARD_BODY_MAX_CHARS;
        if (bodyLength < bodyMin || bodyLength > bodyMax) {
            violations.add(violation("xiaohongshu_body_length",
                    "小红书正文应控制在" + bodyMin + "～" + bodyMax + "字，当前约" + bodyLength + "字"));
        }

        int h2Count = countMatches(H2, body);
        int maxH2 = neutralEducationMode ? NEUTRAL_MAX_H2 : STANDARD_MAX_H2;
        if (h2Count > maxH2) {
            violations.add(violation("xiaohongshu_heading_density",
                    "小红书正文最多使用" + maxH2 + "个二级标题，当前为" + h2Count + "个"));
        }
        if (DEEP_HEADING.matcher(body).find()) {
            violations.add(violation("xiaohongshu_deep_heading",
                    "小红书正文不使用三级及更深层级标题"));
        }
        String genericHeading = firstGenericHeading(body);
        if (StringUtils.hasText(genericHeading)) {
            violations.add(violation("xiaohongshu_generic_heading",
                    "小红书小标题必须概括具体信息，不使用空泛标题：" + genericHeading));
        }

        int listItemCount = countMatches(LIST_ITEM, body);
        int maxListItems = neutralEducationMode ? NEUTRAL_MAX_LIST_ITEMS : STANDARD_MAX_LIST_ITEMS;
        if (listItemCount > maxListItems) {
            violations.add(violation("xiaohongshu_list_density",
                    "小红书正文最多使用" + maxListItems + "个列表项，当前为" + listItemCount + "个"));
        }
        if (TOPIC_TAG.matcher(body).find()) {
            violations.add(violation("xiaohongshu_topic_tag", "小红书正文不得添加话题标签"));
        }
        if (EMOJI.matcher(title + body).find()) {
            violations.add(violation("xiaohongshu_emoji", "小红书文章不得使用 emoji 或营销型符号"));
        }
        return List.copyOf(violations);
    }

    private Violation violation(String type, String message) {
        return new Violation(type, message);
    }

    private String extractTitle(String markdown) {
        for (String line : markdown.split("\\R")) {
            String value = line.trim();
            if (StringUtils.hasText(value)) {
                return value.replaceFirst("^#+\\s*", "").trim();
            }
        }
        return "";
    }

    private String extractBody(String markdown) {
        String[] lines = markdown.split("\\R", -1);
        boolean titleRemoved = false;
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            if (!titleRemoved && StringUtils.hasText(line)) {
                titleRemoved = true;
                continue;
            }
            if (titleRemoved) {
                if (!body.isEmpty()) {
                    body.append('\n');
                }
                body.append(line);
            }
        }
        return body.toString().trim();
    }

    private int plainTextLength(String markdown) {
        String plain = markdown
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*(?:[-*+]\\s+|\\d+[.)、]\\s*)", "")
                .replaceAll("[*_`~>]", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", "");
        return plain.codePointCount(0, plain.length());
    }

    private int countMatches(Pattern pattern, String value) {
        int count = 0;
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String firstContained(String value, List<String> candidates) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return candidates.stream().filter(value::contains).findFirst().orElse(null);
    }

    private String firstBrandReference(String value, Project project, Brand brand) {
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, brand == null ? null : brand.getBrandName());
        addCandidate(candidates, brand == null ? null : brand.getBrandShortName());
        addCandidate(candidates, project == null ? null : project.getBrandName());
        addCandidate(candidates, project == null ? null : project.getCompanyName());
        return firstContained(value, candidates);
    }

    private void addCandidate(List<String> candidates, String value) {
        if (StringUtils.hasText(value) && value.trim().length() >= 2 && !candidates.contains(value.trim())) {
            candidates.add(value.trim());
        }
    }

    private String firstGenericHeading(String markdown) {
        Matcher matcher = H2.matcher(markdown);
        while (matcher.find()) {
            String heading = matcher.group().replaceFirst("^##\\s+", "").trim();
            if (GENERIC_HEADINGS.contains(heading)) {
                return heading;
            }
        }
        return null;
    }

    public record Violation(String type, String message) {
    }
}

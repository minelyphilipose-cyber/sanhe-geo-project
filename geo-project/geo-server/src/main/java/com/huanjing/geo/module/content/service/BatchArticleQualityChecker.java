package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchArticleQualityChecker {

    private static final List<String> AI_CLICHES = List.of(
            "随着", "在当今", "众所周知", "毋庸置疑", "值得注意的是",
            "需要指出的是", "综上所述", "总而言之", "一站式", "赋能", "闭环"
    );
    private static final List<String> EMPTY_HEADINGS = List.of("# 前言", "## 前言", "## 背景", "## 总结", "## 结语");

    private final ObjectMapper objectMapper;

    public QualityResult check(String markdown, Brand brand, List<String> projectForbiddenPhrases) {
        List<Issue> issues = new ArrayList<>();
        if (!StringUtils.hasText(markdown)) {
            issues.add(new Issue("empty_content", "生成内容为空", true));
            return result(issues);
        }
        if (containsEmoji(markdown)) {
            issues.add(new Issue("emoji", "内容包含 emoji，不适合 GEO 引用场景", true));
        }
        for (String heading : EMPTY_HEADINGS) {
            if (markdown.contains(heading)) {
                issues.add(new Issue("empty_heading", "存在空泛标题：" + heading, false));
            }
        }
        long clicheCount = AI_CLICHES.stream().filter(markdown::contains).count();
        if (clicheCount >= 3) {
            issues.add(new Issue("ai_cliche", "AI 高频套话命中较多", true));
        } else if (clicheCount > 0) {
            issues.add(new Issue("ai_cliche", "存在少量 AI 高频套话", false));
        }
        if (projectForbiddenPhrases != null) {
            for (String phrase : projectForbiddenPhrases) {
                if (StringUtils.hasText(phrase) && markdown.contains(phrase.trim())) {
                    issues.add(new Issue("forbidden_phrase", "命中项目禁用词：" + phrase.trim(), true));
                }
            }
        }
        if (brand != null && StringUtils.hasText(brand.getBrandName())) {
            int count = countOccurrences(markdown, brand.getBrandName().trim());
            if (count > 2) {
                issues.add(new Issue("brand_overexposure", "品牌名出现超过 2 次", true));
            }
        }
        return result(issues);
    }

    public String toJson(QualityResult result) {
        try {
            return objectMapper.writeValueAsString(result.issues());
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private QualityResult result(List<Issue> issues) {
        boolean rewriteRequired = issues.stream().anyMatch(Issue::rewriteRequired);
        String status = issues.isEmpty() ? "passed" : "warning";
        return new QualityResult(status, rewriteRequired, issues);
    }

    private boolean containsEmoji(String value) {
        return value.codePoints().anyMatch(codePoint ->
                codePoint >= 0x1F300 && codePoint <= 0x1FAFF
                        || codePoint >= 0x2600 && codePoint <= 0x27BF
        );
    }

    private int countOccurrences(String source, String target) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    public record QualityResult(String status, boolean rewriteRequired, List<Issue> issues) {
    }

    public record Issue(String type, String message, boolean rewriteRequired) {
    }
}

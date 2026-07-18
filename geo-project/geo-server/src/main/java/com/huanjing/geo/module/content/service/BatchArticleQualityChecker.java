package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BatchArticleQualityChecker {

    private static final List<String> EMPTY_HEADINGS = List.of("# 前言", "## 前言", "## 背景", "## 总结", "## 结语");
    private static final Pattern UNRESOLVED_VARIABLE = Pattern.compile("\\{\\{[^{}]+}}|\\$\\{[^{}]+}");

    private final ObjectMapper objectMapper;

    public QualityResult check(String markdown, Brand brand, List<String> projectForbiddenPhrases) {
        List<Issue> issues = new ArrayList<>();
        if (!StringUtils.hasText(markdown)) {
            issues.add(new Issue("empty_content", "生成内容为空", true));
            return result(issues);
        }
        if (UNRESOLVED_VARIABLE.matcher(markdown).find()) {
            issues.add(new Issue("unresolved_variable", "内容包含未替换模板变量", true));
        }
        if (markdown.contains("```markdown") || markdown.contains("```html")) {
            issues.add(new Issue("invalid_format", "正文残留外层代码块标记", true));
        }
        for (String heading : EMPTY_HEADINGS) {
            if (markdown.contains(heading)) {
                issues.add(new Issue("empty_heading", "存在空泛标题：" + heading, false));
            }
        }
        for (String phrase : ArticleForbiddenPhrasePolicy.effectivePhrases(projectForbiddenPhrases)) {
            if (markdown.contains(phrase)) {
                issues.add(new Issue("forbidden_phrase", "命中项目禁用词：" + phrase, true));
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

    public QualityResult withWarning(QualityResult result, String type, String message) {
        List<Issue> issues = new ArrayList<>(result == null ? List.of() : result.issues());
        issues.add(new Issue(type, message, false));
        return result(issues);
    }

    private QualityResult result(List<Issue> issues) {
        boolean rewriteRequired = issues.stream().anyMatch(Issue::rewriteRequired);
        String status = issues.isEmpty() ? "passed" : "warning";
        return new QualityResult(status, rewriteRequired, issues);
    }

    public record QualityResult(String status, boolean rewriteRequired, List<Issue> issues) {
    }

    public record Issue(String type, String message, boolean rewriteRequired) {
    }
}

package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ArticleAiDraftPromptFilter {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9A-Za-z])\\d{17}[0-9Xx](?![0-9A-Za-z])");
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{13,19}(?!\\d)");

    private final SysDictItemMapper sysDictItemMapper;

    public String filterOutboundPrompt(String prompt, Project project, Brand brand) {
        String value = redactPii(prompt == null ? "" : prompt);
        for (String phrase : sensitivePhrases(project, brand)) {
            value = value.replace(phrase, "[SENSITIVE_REDACTED]");
        }
        return value;
    }

    public String filterGeneratedContent(String content, Project project, Brand brand) {
        return filterOutboundPrompt(content, project, brand);
    }

    private String redactPii(String value) {
        return BANK_CARD.matcher(ID_CARD.matcher(MOBILE.matcher(EMAIL.matcher(value)
                .replaceAll("[EMAIL_REDACTED]"))
                .replaceAll("[PHONE_REDACTED]"))
                .replaceAll("[ID_REDACTED]"))
                .replaceAll("[NUMBER_REDACTED]");
    }

    private List<String> sensitivePhrases(Project project, Brand brand) {
        Set<String> words = new LinkedHashSet<>();
        if (brand != null) {
            words.addAll(parseJsonArray(brand.getForbiddenPhrases()));
        }
        if (project != null) {
            words.addAll(parseJsonArray(project.getExtraForbiddenPhrases()));
        }
        List<SysDictItem> globals = sysDictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, "global_forbidden_phrase")
                        .eq(SysDictItem::getEnabled, true)
        );
        for (SysDictItem item : globals == null ? List.<SysDictItem>of() : globals) {
            if (StringUtils.hasText(item.getDictKey())) {
                words.add(item.getDictKey().trim());
            }
        }
        return words.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(word -> word.length() >= 2)
                .distinct()
                .toList();
    }

    private List<String> parseJsonArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> result = new ArrayList<>();
            JSONUtil.parseArray(raw).stream()
                    .filter(item -> item != null && StringUtils.hasText(String.valueOf(item)))
                    .map(item -> String.valueOf(item).trim())
                    .forEach(result::add);
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }
}

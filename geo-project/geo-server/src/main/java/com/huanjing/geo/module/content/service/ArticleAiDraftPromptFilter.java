package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ArticleAiDraftPromptFilter {

    private static final String SENSITIVE_REDACTED_KEY = "SENSITIVE_REDACTED";
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9A-Za-z])\\d{17}[0-9Xx](?![0-9A-Za-z])");
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)(\\d{13,19})(?!\\d)");

    private final SysDictItemMapper sysDictItemMapper;

    public String filterOutboundPrompt(String prompt, Project project, Brand brand) {
        return restoreSensitiveDictionaryValues(redactPii(prompt == null ? "" : prompt));
    }

    public String filterGeneratedContent(String content, Project project, Brand brand) {
        return filterOutboundPrompt(content, project, brand);
    }

    private String redactPii(String value) {
        return redactBankCards(ID_CARD.matcher(MOBILE.matcher(EMAIL.matcher(value)
                .replaceAll("[EMAIL_REDACTED]"))
                .replaceAll("[PHONE_REDACTED]"))
                .replaceAll("[ID_REDACTED]"));
    }

    private String redactBankCards(String value) {
        Matcher matcher = BANK_CARD.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String digits = matcher.group(1);
            matcher.appendReplacement(result, luhnValid(digits) ? "[NUMBER_REDACTED]" : digits);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private boolean luhnValid(String digits) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (doubleDigit) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private String restoreSensitiveDictionaryValues(String value) {
        if (!StringUtils.hasText(value) || !value.contains(SENSITIVE_REDACTED_KEY)) {
            return value;
        }
        List<SysDictItem> items = sysDictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictKey, SENSITIVE_REDACTED_KEY)
                        .eq(SysDictItem::getEnabled, true)
        );
        String restored = value;
        for (SysDictItem item : items == null ? List.<SysDictItem>of() : items) {
            if (StringUtils.hasText(item.getDictValue())) {
                String replacement = item.getDictValue().trim();
                restored = restored
                        .replace("[" + SENSITIVE_REDACTED_KEY + "]", replacement)
                        .replace(SENSITIVE_REDACTED_KEY, replacement);
            }
        }
        return restored;
    }
}

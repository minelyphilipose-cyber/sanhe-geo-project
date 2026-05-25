package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final Pattern ADDRESS_LIKE = Pattern.compile(
            "[^\\r\\n，,。；;]{0,30}(?:省|市|区|县)[^\\r\\n，,。；;]{0,30}(?:路|街|巷|号|栋|楼|室|门店|网点)[^\\r\\n，,。；;]{0,30}"
    );

    private final SysDictItemMapper sysDictItemMapper;

    public String filterOutboundPrompt(String prompt, Project project, Brand brand) {
        return filterOutboundPrompt(prompt, project, brand, false);
    }

    public String filterOutboundPrompt(String prompt, Project project, Brand brand, boolean allowContactInfo) {
        return restoreSensitiveDictionaryValues(redactPii(prompt == null ? "" : prompt, brand, allowContactInfo));
    }

    public String filterGeneratedContent(String content, Project project, Brand brand) {
        return filterGeneratedContent(content, project, brand, false);
    }

    public String filterGeneratedContent(String content, Project project, Brand brand, boolean allowContactInfo) {
        return removeGeneratedRedactionMarkers(filterOutboundPrompt(content, project, brand, allowContactInfo));
    }

    private String redactPii(String value, Brand brand, boolean allowContactInfo) {
        String redacted = EMAIL.matcher(value).replaceAll("[EMAIL_REDACTED]");
        redacted = redactMobiles(redacted, allowedPhones(brand, allowContactInfo));
        redacted = redactAddresses(redacted, brand, allowContactInfo);
        return redactBankCards(ID_CARD.matcher(redacted).replaceAll("[ID_REDACTED]"));
    }

    private String removeGeneratedRedactionMarkers(String value) {
        if (!StringUtils.hasText(value) || !value.contains("_REDACTED]")) {
            return value;
        }
        StringBuilder cleaned = new StringBuilder();
        for (String line : value.split("\\r?\\n", -1)) {
            String replaced = line
                    .replace("[ADDRESS_REDACTED]", "")
                    .replace("[PHONE_REDACTED]", "")
                    .replace("[EMAIL_REDACTED]", "")
                    .replace("[ID_REDACTED]", "")
                    .replace("[NUMBER_REDACTED]", "")
                    .replaceAll("[：:，,。；;、\\s]+$", "")
                    .trim();
            if (line.contains("_REDACTED]") && isEmptyContactLine(replaced)) {
                continue;
            }
            cleaned.append(replaced).append('\n');
        }
        return cleaned.toString().trim();
    }

    private boolean isEmptyContactLine(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = value.replaceAll("[#*\\-\\s：:，,。；;、]", "");
        return normalized.isEmpty()
                || "地址".equals(normalized)
                || "电话".equals(normalized)
                || "邮箱".equals(normalized)
                || "联系方式".equals(normalized);
    }

    private String redactMobiles(String value, Set<String> allowedPhones) {
        Matcher matcher = MOBILE.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String phone = matcher.group();
            String replacement = allowedPhones.contains(normalizeDigits(phone)) ? phone : "[PHONE_REDACTED]";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String redactAddresses(String value, Brand brand, boolean allowContactInfo) {
        String allowedAddress = allowContactInfo && brand != null && StringUtils.hasText(brand.getPublicAddress())
                ? brand.getPublicAddress().trim()
                : null;
        Matcher matcher = ADDRESS_LIKE.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String address = matcher.group();
            String replacement = isAllowedAddress(address, allowedAddress) ? address : "[ADDRESS_REDACTED]";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Set<String> allowedPhones(Brand brand, boolean allowContactInfo) {
        if (!allowContactInfo || brand == null || !StringUtils.hasText(brand.getPublicPhone())) {
            return Set.of();
        }
        Set<String> phones = new HashSet<>();
        String publicPhone = brand.getPublicPhone();
        Matcher matcher = MOBILE.matcher(publicPhone);
        while (matcher.find()) {
            phones.add(normalizeDigits(matcher.group()));
        }
        String normalized = normalizeDigits(publicPhone);
        if (phones.isEmpty() && StringUtils.hasText(normalized)) {
            phones.add(normalized);
        }
        return phones;
    }

    private boolean isAllowedAddress(String address, String allowedAddress) {
        if (!StringUtils.hasText(address) || !StringUtils.hasText(allowedAddress)) {
            return false;
        }
        String normalizedAddress = normalizeAddress(address);
        String normalizedAllowed = normalizeAddress(allowedAddress);
        return normalizedAddress.contains(normalizedAllowed) || normalizedAllowed.contains(normalizedAddress);
    }

    private String normalizeDigits(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("\\D", "") : "";
    }

    private String normalizeAddress(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("\\s", "") : "";
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

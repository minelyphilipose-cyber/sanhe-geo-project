package com.huanjing.geo.module.presale.dto.snapshot.computed;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 售前意图编码白名单。
 *
 * <p>声明顺序即输出顺序:
 * RECOMMENDATION / COMPARISON / INQUIRY / COGNITIVE / SCENARIO。</p>
 */
public enum PresaleIntentCode {
    RECOMMENDATION("RECOMMENDATION", "推荐型"),
    COMPARISON("COMPARISON", "对比型"),
    INQUIRY("INQUIRY", "问题型"),
    COGNITIVE("COGNITIVE", "认知型"),
    SCENARIO("SCENARIO", "场景型");

    private static final Map<String, PresaleIntentCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    it -> it.code.toUpperCase(Locale.ROOT),
                    Function.identity()));

    private static final Map<String, PresaleIntentCode> BY_LABEL = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PresaleIntentCode::getLabel, Function.identity()));

    private final String code;
    private final String label;

    PresaleIntentCode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static List<PresaleIntentCode> allInOrder() {
        return List.of(values());
    }

    public static PresaleIntentCode fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("intent code is null");
        }
        PresaleIntentCode mapped = BY_CODE.get(code.toUpperCase(Locale.ROOT));
        if (mapped == null) {
            throw new IllegalArgumentException("unsupported intent code: " + code);
        }
        return mapped;
    }

    public static PresaleIntentCode fromLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("intent label is null");
        }
        PresaleIntentCode mapped = BY_LABEL.get(label);
        if (mapped == null) {
            throw new IllegalArgumentException("unsupported intent label: " + label);
        }
        return mapped;
    }

    public static String labelOf(String code) {
        return fromCode(code).getLabel();
    }
}


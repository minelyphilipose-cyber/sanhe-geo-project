package com.huanjing.geo.module.presale.ruleengine.util;

import java.util.List;
import java.util.stream.Collectors;

/**
 * evidence_data 文本拼接工具。
 *
 * <p>按设计文档 §5 固定 3 种拼接格式,10 个 Builder 共用,避免文本风格漂移。</p>
 */
public final class TextFormatUtil {

    private TextFormatUtil() {
    }

    /**
     * 平台 + 百分比格式,如 {@code "ChatGPT(28%)、Claude(18%)"}。
     * 用于 {@code weak_platforms_text} / {@code strong_platforms_text}。
     */
    public static String formatPlatformsWithRate(List<PlatformRateEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        return entries.stream()
                .map(e -> String.format("%s(%d%%)", e.getName(), Math.round(e.getRate())))
                .collect(Collectors.joining("、"));
    }

    /**
     * 纯平台名拼接,如 {@code "豆包"} 或 {@code "豆包、文心一言"}。
     * 用于 {@code affected_platforms_text} / {@code degraded_platforms_text} / {@code uncovered_platforms_text}。
     */
    public static String formatPlatformNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        return String.join("、", names);
    }

    /**
     * 场景列表拼接,中文引号包裹,如 {@code "\u201c北京最正宗火锅店\u201d、\u201c北京约会吃火锅推荐\u201d"}。
     * 用于 {@code missed_scenes_text}。
     */
    public static String formatQuotedScenes(List<String> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return "";
        }
        return scenes.stream()
                .map(s -> "\u201c" + s + "\u201d")
                .collect(Collectors.joining("、"));
    }

    /** 平台 + 提及率的条目对象,仅供 TextFormatUtil 和 PlatformStatUtil 共用。 */
    public static class PlatformRateEntry {
        private final String name;
        private final double rate;

        public PlatformRateEntry(String name, double rate) {
            this.name = name;
            this.rate = rate;
        }

        public String getName() {
            return name;
        }

        public double getRate() {
            return rate;
        }
    }
}

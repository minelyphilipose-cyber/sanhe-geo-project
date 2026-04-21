package com.huanjing.geo.module.presale.ruleengine.util;

import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 平台聚合工具,供 3 个复杂 Builder 共用:
 * RULE_PLATFORM_IMBALANCE / RULE_PLATFORM_COVERAGE_NARROW / RULE_SINGLE_PLATFORM_DOMINANT。
 *
 * <p>语义约定:</p>
 * <ul>
 *   <li>"有效平台" = 未降级的平台(is_degraded=false)</li>
 *   <li>聚合计算(强/弱/首推)只基于有效平台,降级平台剔除</li>
 *   <li>涉及 mentionRate 排序时,null 值视为 0 参与比较(避免 NPE)</li>
 * </ul>
 */
public final class PlatformStatUtil {

    private PlatformStatUtil() {
    }

    /** 过滤出非降级的平台。null 列表返回空列表,不抛 NPE。 */
    public static List<PlatformBreakdown> effective(List<PlatformBreakdown> all) {
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }
        List<PlatformBreakdown> result = new ArrayList<>();
        for (PlatformBreakdown p : all) {
            if (p == null) continue;
            if (Boolean.TRUE.equals(p.getIsDegraded())) continue;
            result.add(p);
        }
        return result;
    }

    /** 降级平台的 platform_name 列表,用于 degraded_platforms_text。 */
    public static List<String> degradedNames(List<PlatformBreakdown> all) {
        List<String> names = new ArrayList<>();
        if (all == null) return names;
        for (PlatformBreakdown p : all) {
            if (p == null) continue;
            if (Boolean.TRUE.equals(p.getIsDegraded())) {
                names.add(p.getPlatformName());
            }
        }
        return names;
    }

    /** 未覆盖(mention_count == 0)的有效平台名列表。 */
    public static List<String> uncoveredNames(List<PlatformBreakdown> all) {
        List<String> names = new ArrayList<>();
        for (PlatformBreakdown p : effective(all)) {
            Integer mc = p.getMentionCount();
            if (mc == null || mc == 0) {
                names.add(p.getPlatformName());
            }
        }
        return names;
    }

    /** 有提及(mention_count > 0)的有效平台数。 */
    public static int coveredCount(List<PlatformBreakdown> all) {
        int cnt = 0;
        for (PlatformBreakdown p : effective(all)) {
            Integer mc = p.getMentionCount();
            if (mc != null && mc > 0) cnt++;
        }
        return cnt;
    }

    /**
     * 按 mention_rate 降序排序的有效平台。null rate 视为 0。
     * 主要服务 RULE_PLATFORM_IMBALANCE 的 strong/weak 分组。
     */
    public static List<PlatformBreakdown> sortedByMentionRateDesc(List<PlatformBreakdown> all) {
        List<PlatformBreakdown> effective = effective(all);
        effective.sort(Comparator.comparingDouble(
                (PlatformBreakdown p) -> safeRate(p.getMentionRate())).reversed());
        return effective;
    }

    /**
     * 返回 primaryRecommendationCount 最大的平台。列表空或全 null 时返回 null。
     * 服务 RULE_SINGLE_PLATFORM_DOMINANT。
     */
    public static PlatformBreakdown topByPrimaryRecommendation(List<PlatformBreakdown> all) {
        PlatformBreakdown best = null;
        int bestCnt = -1;
        for (PlatformBreakdown p : effective(all)) {
            Integer cnt = p.getPrimaryRecommendationCount();
            int v = cnt == null ? 0 : cnt;
            if (v > bestCnt) {
                bestCnt = v;
                best = p;
            }
        }
        return best;
    }

    /** 所有有效平台的 primaryRecommendationCount 求和(null 视为 0)。 */
    public static int sumPrimaryRecommendation(List<PlatformBreakdown> all) {
        int sum = 0;
        for (PlatformBreakdown p : effective(all)) {
            Integer cnt = p.getPrimaryRecommendationCount();
            if (cnt != null) sum += cnt;
        }
        return sum;
    }

    /**
     * 转换为 TextFormatUtil 可消费的 PlatformRateEntry 列表。
     * @param platforms 有效平台列表
     */
    public static List<TextFormatUtil.PlatformRateEntry> toRateEntries(List<PlatformBreakdown> platforms) {
        List<TextFormatUtil.PlatformRateEntry> entries = new ArrayList<>();
        if (platforms == null) return entries;
        for (PlatformBreakdown p : platforms) {
            if (p == null) continue;
            entries.add(new TextFormatUtil.PlatformRateEntry(
                    p.getPlatformName(),
                    safeRate(p.getMentionRate())));
        }
        return entries;
    }

    private static double safeRate(Double rate) {
        return Objects.requireNonNullElse(rate, 0.0);
    }
}

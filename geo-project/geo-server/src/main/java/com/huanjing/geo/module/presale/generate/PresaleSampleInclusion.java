package com.huanjing.geo.module.presale.generate;

/**
 * 样本纳入口径。
 *
 * <p>平台统计与 platform_intent_breakdown 必须共享同一判定逻辑,避免口径漂移。</p>
 */
public final class PresaleSampleInclusion {

    private PresaleSampleInclusion() {
    }

    public static boolean isIncluded(String callStatus, Integer isExcluded, boolean platformDegraded) {
        if (platformDegraded) {
            return false;
        }
        boolean success = "SUCCESS".equalsIgnoreCase(callStatus);
        boolean excluded = isExcluded != null && isExcluded == 1;
        return success && !excluded;
    }
}


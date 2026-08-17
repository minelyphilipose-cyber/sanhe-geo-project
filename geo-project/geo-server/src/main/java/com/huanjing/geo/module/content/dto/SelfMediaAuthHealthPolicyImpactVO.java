package com.huanjing.geo.module.content.dto;

public record SelfMediaAuthHealthPolicyImpactVO(
        String platform,
        int activeAccountCount,
        int currentlyOverdueCount,
        int projectedOverdueCount,
        int projectedDueSoonCount,
        int newlyOverdueCount,
        String customerImpact
) {
}

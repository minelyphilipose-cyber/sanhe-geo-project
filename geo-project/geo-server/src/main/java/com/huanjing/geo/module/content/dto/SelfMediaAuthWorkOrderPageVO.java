package com.huanjing.geo.module.content.dto;

import java.util.List;

public record SelfMediaAuthWorkOrderPageVO(
        List<SelfMediaAuthWorkOrderVO> records,
        long total,
        long current,
        long size,
        long unclaimedCount,
        long processingCount,
        long resolvedCount
) {
}

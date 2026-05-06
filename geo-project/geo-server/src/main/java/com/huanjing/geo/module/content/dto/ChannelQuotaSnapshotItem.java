package com.huanjing.geo.module.content.dto;

import lombok.Data;

@Data
public class ChannelQuotaSnapshotItem {
    private String channelCode;
    private String periodType;
    private int quotaLimit;
    private boolean enabled;
}

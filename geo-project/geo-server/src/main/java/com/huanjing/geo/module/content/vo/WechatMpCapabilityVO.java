package com.huanjing.geo.module.content.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WechatMpCapabilityVO {
    private boolean draftDistributionEnabled;
    private boolean autoPublishEnabled;
    private String clientMode;
    private String reason;
    private String description;
}

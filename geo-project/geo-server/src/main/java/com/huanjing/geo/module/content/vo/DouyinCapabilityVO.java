package com.huanjing.geo.module.content.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DouyinCapabilityVO {
    private boolean enabled;
    private String mode;
    private String disabledReason;
    private boolean liveVerificationBlocked;
    private String liveVerificationReason;
    private String description;
    private List<DouyinReadinessCheckVO> readinessChecks;
}

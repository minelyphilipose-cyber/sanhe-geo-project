package com.huanjing.geo.module.content.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DouyinCapabilityVO {
    private boolean enabled;
    private String mode;
    private String disabledReason;
}

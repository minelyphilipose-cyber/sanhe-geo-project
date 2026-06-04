package com.huanjing.geo.module.content.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SelfMediaPublishScheduleRejectedItemVO {
    private Long articleId;
    private Long selfMediaAccountId;
    private String platform;
    private String code;
    private String message;
    private String settingPath;
    private Long existingScheduleId;
}

package com.huanjing.geo.module.content.vo;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelfMediaPublishScheduleRejectedItemVO {
    private Long articleId;
    private Long selfMediaAccountId;
    private String platform;
    private String code;
    private String message;
    private String settingPath;
    private Long existingScheduleId;
}

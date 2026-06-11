package com.huanjing.geo.module.content.dto;

import lombok.Data;

@Data
public class ProjectSelfMediaScheduleConfigRequest {
    private Boolean autoScheduleEnabled;
    private String defaultScheduleStrategy;
    private Boolean includeAdjustedWorkdays;
    private String remark;
}

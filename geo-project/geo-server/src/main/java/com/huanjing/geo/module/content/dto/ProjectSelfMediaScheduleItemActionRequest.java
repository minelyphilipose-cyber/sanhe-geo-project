package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectSelfMediaScheduleItemActionRequest {
    @Size(max = 512)
    private String reason;
}

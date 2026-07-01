package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectStartRequestApproveRequest {
    private Long assignedInternalOwnerId;

    @Size(max = 500)
    private String reviewRemark;
}

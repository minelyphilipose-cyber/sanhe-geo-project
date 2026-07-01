package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectSetupReadyRequest {
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}

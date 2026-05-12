package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BaselinePollStartRequest {
    @NotEmpty
    private List<String> platformCodes;

    @NotEmpty
    private List<String> questionTiers;
}

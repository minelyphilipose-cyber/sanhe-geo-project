package com.huanjing.geo.module.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DispatchTaskReplayRequest {
    @NotNull
    private Long taskId;
}

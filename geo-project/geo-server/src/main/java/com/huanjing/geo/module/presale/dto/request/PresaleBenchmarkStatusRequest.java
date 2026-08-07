package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PresaleBenchmarkStatusRequest {
    @NotNull
    private Boolean enabled;
    private String remark;
}

package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PresaleHeatmapSummaryUpdateRequest {
    @NotBlank
    @Size(max = 500)
    private String summaryTemplate;

    @NotBlank
    @Size(max = 300)
    private String colorLegendTemplate;

    @NotNull
    private Integer sortOrder;

    @NotNull
    private Boolean enabled;

    @Size(max = 255)
    private String remark;
}

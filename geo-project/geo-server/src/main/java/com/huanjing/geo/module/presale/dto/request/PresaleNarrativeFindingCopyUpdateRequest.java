package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PresaleNarrativeFindingCopyUpdateRequest {
    @NotBlank
    @Size(max = 200)
    private String titleTemplate;

    @NotBlank
    @Size(max = 1000)
    private String bodyTemplate;

    @NotBlank
    @Size(max = 300)
    private String evidenceTemplate;

    @NotNull
    private Integer priority;

    @NotNull
    private Boolean enabled;

    @Size(max = 255)
    private String remark;
}

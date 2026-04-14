package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DictItemCreateRequest {

    @NotBlank(message = "dictType is required")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "dictType format invalid")
    private String dictType;

    @NotBlank(message = "dictKey is required")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$", message = "dictKey format invalid")
    private String dictKey;

    @NotBlank(message = "dictValue is required")
    private String dictValue;

    @NotNull(message = "sortOrder is required")
    @Min(value = 0, message = "sortOrder must be >= 0")
    private Integer sortOrder;

    private Boolean enabled = true;

    private String remark;
}

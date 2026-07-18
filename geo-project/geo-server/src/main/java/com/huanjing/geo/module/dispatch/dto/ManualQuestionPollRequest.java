package com.huanjing.geo.module.dispatch.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ManualQuestionPollRequest {
    @NotNull
    private Long projectId;

    @NotBlank
    @Pattern(regexp = "(?i)A|B|C")
    private String questionTier;

    @NotEmpty
    @Size(max = 4)
    private List<@NotNull Long> platformIds;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer questionLimit;

    @NotBlank
    @Size(max = 64)
    private String clientRequestId;
}

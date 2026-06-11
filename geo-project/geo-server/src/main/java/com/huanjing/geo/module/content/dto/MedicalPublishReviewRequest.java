package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MedicalPublishReviewRequest {
    @NotBlank
    private String action;
    private String comment;
}

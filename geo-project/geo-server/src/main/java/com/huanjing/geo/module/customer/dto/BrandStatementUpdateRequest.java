package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class BrandStatementUpdateRequest {
    @NotBlank
    private String positioning;
    private List<String> sellingPoints;
    @NotBlank
    private String differentiation;
    @NotBlank
    private String brandParagraph;
}

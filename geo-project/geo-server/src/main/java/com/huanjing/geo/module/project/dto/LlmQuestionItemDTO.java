package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmQuestionItemDTO {
    @Size(max = 64, message = "questionText length must be <= 64")
    private String questionText;

    @Size(max = 30, message = "seedText length must be <= 30")
    private String seedText;
}

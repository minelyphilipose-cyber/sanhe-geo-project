package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArticleRevisionSaveRequest {
    private String title;
    @NotBlank
    private String contentMarkdown;
    private String note;
}


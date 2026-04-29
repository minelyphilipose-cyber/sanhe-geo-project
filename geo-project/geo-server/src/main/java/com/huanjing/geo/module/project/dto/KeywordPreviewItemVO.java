package com.huanjing.geo.module.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeywordPreviewItemVO {
    private String text;
    private String sourceType;
    private String seedText;

    public KeywordPreviewItemVO(String text, String sourceType) {
        this.text = text;
        this.sourceType = sourceType;
    }
}
